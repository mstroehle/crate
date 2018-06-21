/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.integrationtests;

import io.crate.action.sql.SQLOperations;
import io.crate.action.sql.Session;
import io.crate.execution.engine.collect.stats.JobsLogService;
import io.crate.execution.engine.collect.stats.JobsLogs;
import io.crate.expression.reference.sys.job.JobContextLog;
import io.crate.testing.TestingHelpers;
import io.crate.testing.UseJdbc;
import io.crate.testing.UseSemiJoins;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.After;
import org.junit.Test;

import java.util.Iterator;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;

@ESIntegTestCase.ClusterScope(numDataNodes = 2, numClientNodes = 0, supportsDedicatedMasters = false)
@UseSemiJoins(0) // Avoid set session stmt to interfere with tests
public class JobLogIntegrationTest extends SQLTransportIntegrationTest {

    @After
    public void resetSettings() throws Exception {
        // reset stats settings in case of some tests changed it and failed without resetting.
        execute("reset global stats.enabled, stats.jobs_log_size, stats.operations_log_size");
    }

    @Test
    @UseJdbc(0) // SET extra_float_digits = 3 gets added to the jobs_log
    public void testJobLogWithEnabledAndDisabledStats() throws Exception {
        execute("set global transient stats.enabled=true");
        execute("set global transient stats.jobs_log_size=1");

        // We record the statements in the log **after** we notify the result receivers (see {@link JobsLogsUpdateListener usage).
        // So it might happen that the "set global ..." statement execution is returned to this test but the recording
        // in the log is done AFTER the execution of the below "select name from sys.cluster" statement (because async
        // programming is evil like that). And then this test will fail and people will spend days and days to figure
        // out what's going on.
        // So let's just wait for the "set global ... " statement to be recorded here and then move on with our test.
        assertBusy(() -> {
            execute("select stmt from sys.jobs_log order by ended desc");
            assertThat(TestingHelpers.printedTable(response.rows()),
                containsString("set global transient stats.jobs_log_size=1"));
        });

        // wait for the other node to receive the new jobs_log_size setting change instruction
        for (JobsLogService jobsLogService : internalCluster().getDataNodeInstances(JobsLogService.class)) {
            assertBusy(() -> assertThat(jobsLogService.jobsLogSize(), is(1)));
        }

        // Each node can hold only 1 query (the latest one) so in total we should always see 2 queries in
        // the jobs_log. We make sure that we hit both nodes with 2 queries each and then assert that
        // only the latest queries are found in the log.
        for (SQLOperations sqlOperations : internalCluster().getDataNodeInstances(SQLOperations.class)) {
            Session session = sqlOperations.newSystemSession();
            execute("select name from sys.cluster", null, session);
        }
        assertJobLogOnNodesHaveOnlyStatement("select name from sys.cluster");

        for (SQLOperations sqlOperations : internalCluster().getDataNodeInstances(SQLOperations.class)) {
            Session session = sqlOperations.newSystemSession();
            execute("select id from sys.cluster", null, session);
        }
        assertJobLogOnNodesHaveOnlyStatement("select id from sys.cluster");

        execute("set global transient stats.enabled = false");
        for (JobsLogService jobsLogService : internalCluster().getDataNodeInstances(JobsLogService.class)) {
            assertBusy(() -> assertThat(jobsLogService.isEnabled(), is(false)));
        }
        execute("select * from sys.jobs_log");
        assertThat(response.rowCount(), is(0L));
    }

    private void assertJobLogOnNodesHaveOnlyStatement(String statement) throws Exception {
        for (JobsLogService jobsLogService : internalCluster().getDataNodeInstances(JobsLogService.class)) {
            assertBusy(() -> {
                assertThat(jobsLogService.jobsLogSize(), is(1));
                JobsLogs jobsLogs = jobsLogService.get();
                Iterator<JobContextLog> iterator = jobsLogs.jobsLog().iterator();
                if (iterator.hasNext()) {
                    assertThat(iterator.next().statement(), is(statement));
                }
            });
        }
    }
    @Test
    @UseJdbc(0) // set has no rowcount
    public void testSetSingleStatement() throws Exception {
        execute("select settings['stats']['jobs_log_size'] from sys.cluster");
        assertThat(response.rowCount(), is(1L));
        assertThat(response.rows()[0][0], is(JobsLogService.STATS_JOBS_LOG_SIZE_SETTING.getDefault()));

        execute("set global persistent stats.enabled= true, stats.jobs_log_size=7");
        assertThat(response.rowCount(), is(1L));

        execute("select settings['stats']['jobs_log_size'] from sys.cluster");
        assertThat(response.rowCount(), is(1L));
        assertThat(response.rows()[0][0], is(7));

        execute("reset global stats.enabled, stats.jobs_log_size");
        assertThat(response.rowCount(), is(1L));
        waitNoPendingTasksOnAll();

        execute("select settings['stats']['enabled'], settings['stats']['jobs_log_size'] from sys.cluster");
        assertThat(response.rowCount(), is(1L));
        assertThat(response.rows()[0][0], is(JobsLogService.STATS_ENABLED_SETTING.getDefault()));
        assertThat(response.rows()[0][1], is(JobsLogService.STATS_JOBS_LOG_SIZE_SETTING.getDefault()));

    }

    @Test
    public void testEmptyJobsInLog() throws Exception {
        // Setup data
        execute("create table characters (id int primary key, name string)");
        sqlExecutor.ensureYellowOrGreen();

        execute("set global transient stats.enabled = true");
        execute("insert into characters (id, name) values (1, 'sysjobstest')");
        execute("refresh table characters");
        execute("delete from characters where id = 1");
        // make sure everything is deleted (nothing changed in whole class lifecycle cluster state)
        assertThat(response.rowCount(), is(1L));
        execute("refresh table characters");

        execute("select * from sys.jobs_log where stmt like 'insert into%' or stmt like 'delete%'");
        assertThat(response.rowCount(), is(2L));
    }
}

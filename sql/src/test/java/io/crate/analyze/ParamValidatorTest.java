/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.analyze;

import io.crate.test.integration.CrateUnitTest;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.junit.Before;
import org.junit.Test;

public class ParamValidatorTest extends CrateUnitTest {

    private CreateRepositoryAnalyzer.ParamValidator validator;

    @Before
    public void initValidator() throws Exception {
        validator = new CreateRepositoryAnalyzer.ParamValidator();
    }

    @Test
    public void testValidate() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid repository type \"invalid_type\"");
        validator.validate("invalid_type", ImmutableSettings.EMPTY);
    }

    @Test
    public void testRequiredTypeIsMissing() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
                "The following required parameters are missing to create a repository of type \"fs\": [location]");
        validator.validate("fs", ImmutableSettings.EMPTY);
    }

    @Test
    public void testInvalidSetting() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid parameters specified: [yay]");
        Settings settings = ImmutableSettings.builder()
                .put("location", "foo")
                .put("yay", "invalid").build();
        validator.validate("fs", settings);
    }

    @Test
    public void testHdfsDynamicConfParam() throws Exception {
        Settings settings = ImmutableSettings.builder()
                .put("path", "/data")
                .put("conf.foobar", "bar").build();
        validator.validate("hdfs", settings);
    }
}
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

package io.crate.expression.scalar;

import io.crate.data.Input;
import io.crate.metadata.FunctionIdent;
import io.crate.metadata.FunctionInfo;
import io.crate.metadata.Scalar;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.functions.Signature;
import io.crate.types.DataTypes;

import javax.annotation.Nullable;
import java.util.List;

import static io.crate.expression.scalar.array.ArrayArgumentValidators.ensureInnerTypeIsNotUndefined;
import static io.crate.metadata.functions.TypeVariableConstraint.typeVariable;
import static io.crate.types.TypeSignature.parseTypeSignature;

class ArrayLowerFunction extends Scalar<Integer, Object> {

    public static final String NAME = "array_lower";

    public static void register(ScalarFunctionModule module) {
        module.register(
            Signature.scalar(
                NAME,
                parseTypeSignature("array(E)"),
                DataTypes.INTEGER.getTypeSignature(),
                DataTypes.INTEGER.getTypeSignature()
            ).withTypeVariableConstraints(typeVariable("E")),
            (signature, argumentTypes) -> {
                ensureInnerTypeIsNotUndefined(argumentTypes, NAME);
                return new ArrayLowerFunction(
                    new FunctionInfo(new FunctionIdent(NAME, argumentTypes), DataTypes.INTEGER),
                    signature
                );
            }
        );
    }

    private final FunctionInfo functionInfo;
    private final Signature signature;

    private ArrayLowerFunction(FunctionInfo functionInfo, Signature signature) {
        this.functionInfo = functionInfo;
        this.signature = signature;
    }

    @Override
    public FunctionInfo info() {
        return functionInfo;
    }

    @Nullable
    @Override
    public Signature signature() {
        return signature;
    }

    @Override
    public Integer evaluate(TransactionContext txnCtx, Input[] args) {
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) args[0].value();
        Object dimension1Indexed = args[1].value();
        if (values == null || values.isEmpty() || dimension1Indexed == null) {
            return null;
        }

        // sql dimensions are 1 indexed
        int dimension = (int) dimension1Indexed - 1;

        try {
            Object dimensionValue = values.get(dimension);
            if (dimensionValue instanceof List) {
                List dimensionArray = (List) dimensionValue;
                return dimensionArray.size() > 0 ? 1 : null;
            }
            return 1;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
}

/*
 * Copyright 2019-2022 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.languages;

import lombok.Getter;
import org.apache.calcite.avatica.util.TimeUnit;
import org.polypheny.db.algebra.constant.FunctionCategory;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.fun.AggFunction;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.Catalog.QueryLanguage;
import org.polypheny.db.nodes.IntervalQualifier;
import org.polypheny.db.nodes.Operator;
import org.polypheny.db.type.checker.PolySingleOperandTypeChecker;
import org.polypheny.db.type.inference.PolyOperandTypeInference;
import org.polypheny.db.type.inference.PolyReturnTypeInference;

/**
 * LanguageManager is responsible for providing a way of accessing objects and functions of the different available languages.
 */
public abstract class LanguageManager {

    @Getter
    private static LanguageManager instance;


    public static synchronized LanguageManager setAndGetInstance( LanguageManager manager ) {
        if ( manager != null ) {
            instance = manager;
        }

        return instance;
    }

    public abstract IntervalQualifier createIntervalQualifier(
            QueryLanguage language,
            TimeUnit startUnit,
            int startPrecision,
            TimeUnit endUnit,
            int fractionalSecondPrecision,
            ParserPos zero );

    public abstract AggFunction createMinMaxAggFunction( QueryLanguage language, Kind kind );

    public abstract AggFunction createSumEmptyIsZeroFunction( QueryLanguage language );

    public abstract AggFunction createBitOpAggFunction( QueryLanguage language, Kind kind );

    public abstract AggFunction createSumAggFunction( QueryLanguage language, AlgDataType type );

    public abstract Operator createFunction(
            QueryLanguage language,
            String artificial_selectivity,
            Kind otherFunction,
            PolyReturnTypeInference aBoolean,
            PolyOperandTypeInference o,
            PolySingleOperandTypeChecker numeric,
            FunctionCategory system );


}

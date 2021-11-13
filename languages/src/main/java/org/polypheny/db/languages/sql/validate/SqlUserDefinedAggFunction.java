/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.db.languages.sql.validate;


import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.calcite.linq4j.function.Experimental;
import org.polypheny.db.core.UserDefined;
import org.polypheny.db.jdbc.JavaTypeFactoryImpl;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.rel.type.RelDataTypeFactoryImpl.JavaType;
import org.polypheny.db.schema.AggregateFunction;
import org.polypheny.db.schema.FunctionParameter;
import org.polypheny.db.languages.sql.SqlAggFunction;
import org.polypheny.db.core.FunctionCategory;
import org.polypheny.db.languages.sql.SqlIdentifier;
import org.polypheny.db.core.Kind;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.checker.PolyOperandTypeChecker;
import org.polypheny.db.type.inference.PolyOperandTypeInference;
import org.polypheny.db.type.inference.PolyReturnTypeInference;
import org.polypheny.db.util.Optionality;
import org.polypheny.db.util.Util;


/**
 * User-defined aggregate function.
 *
 * Created by the validator, after resolving a function call to a function defined in a Polypheny-DB schema.
 */
public class SqlUserDefinedAggFunction extends SqlAggFunction implements UserDefined {

    @Getter
    public final AggregateFunction function;

    /**
     * This field is is technical debt; see "Remove RelDataTypeFactory argument from SqlUserDefinedAggFunction constructor".
     */
    @Experimental
    public final RelDataTypeFactory typeFactory;


    /**
     * Creates a SqlUserDefinedAggFunction.
     */
    public SqlUserDefinedAggFunction(
            SqlIdentifier opName,
            PolyReturnTypeInference returnTypeInference,
            PolyOperandTypeInference operandTypeInference,
            PolyOperandTypeChecker operandTypeChecker,
            AggregateFunction function,
            boolean requiresOrder,
            boolean requiresOver,
            Optionality requiresGroupOrder,
            RelDataTypeFactory typeFactory ) {
        super(
                Util.last( opName.names ),
                opName,
                Kind.OTHER_FUNCTION,
                returnTypeInference,
                operandTypeInference,
                operandTypeChecker,
                FunctionCategory.USER_DEFINED_FUNCTION,
                requiresOrder,
                requiresOver,
                requiresGroupOrder );
        this.function = function;
        this.typeFactory = typeFactory;
    }


    @Override
    public List<RelDataType> getParamTypes() {
        List<RelDataType> argTypes = new ArrayList<>();
        for ( FunctionParameter o : function.getParameters() ) {
            final RelDataType type = o.getType( typeFactory );
            argTypes.add( type );
        }
        return toSql( argTypes );
    }


    private List<RelDataType> toSql( List<RelDataType> types ) {
        return Lists.transform( types, this::toSql );
    }


    private RelDataType toSql( RelDataType type ) {
        if ( type instanceof JavaType && ((JavaType) type).getJavaClass() == Object.class ) {
            return typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.ANY ), true );
        }
        return JavaTypeFactoryImpl.toSql( typeFactory, type );
    }

}


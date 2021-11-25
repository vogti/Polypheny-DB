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

package org.polypheny.db.core;

import java.util.List;
import org.polypheny.db.core.enums.Kind;
import org.polypheny.db.core.enums.Syntax;
import org.polypheny.db.core.nodes.Call;
import org.polypheny.db.core.nodes.Literal;
import org.polypheny.db.core.nodes.Node;
import org.polypheny.db.core.nodes.OperatorBinding;
import org.polypheny.db.core.nodes.OperatorImpl;
import org.polypheny.db.core.validate.Validator;
import org.polypheny.db.core.validate.ValidatorScope;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.type.checker.PolyOperandTypeChecker;
import org.polypheny.db.type.inference.PolyOperandTypeInference;
import org.polypheny.db.type.inference.PolyReturnTypeInference;

public class SpecialOperator extends OperatorImpl {

    public SpecialOperator( String name, Kind kind ) {
        this( name, kind, null, null, null );
    }


    public SpecialOperator( String name, Kind kind, PolyReturnTypeInference returnTypeInference, PolyOperandTypeInference operandTypeInference, PolyOperandTypeChecker operandTypeChecker ) {
        super( name, kind, returnTypeInference, operandTypeInference, operandTypeChecker );
    }


    @Override
    public Syntax getSyntax() {
        return Syntax.SPECIAL;
    }


    @Override
    public Call createCall( Literal functionQualifier, ParserPos pos, Node... operands ) {
        return null;
    }


    @Override
    public RelDataType inferReturnType( OperatorBinding opBinding ) {
        return null;
    }


    @Override
    public RelDataType deriveType( Validator validator, ValidatorScope scope, Call call ) {
        return null;
    }


    @Override
    public RelDataType inferReturnType( RelDataTypeFactory typeFactory, List<RelDataType> operandTypes ) {
        return null;
    }

}

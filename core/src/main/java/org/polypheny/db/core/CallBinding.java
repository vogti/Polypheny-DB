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
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.runtime.PolyphenyDbException;
import org.polypheny.db.runtime.Resources;

public interface CallBinding {

    Validator getValidator();

    ValidatorScope getScope();

    List<Node> operands();

    int getOperandCount();

    RelDataType getOperandType( int ordinal );

    PolyphenyDbException newError( Resources.ExInst<SqlValidatorException> e );

    PolyphenyDbException newValidationSignatureError();

    PolyphenyDbException newValidationError( Resources.ExInst<SqlValidatorException> ex );

}

/*
 * Copyright 2019-2020 The Polypheny Project
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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.sql.advise;


import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.runtime.PolyphenyDbException;
import org.polypheny.db.sql.SqlCall;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.SqlOperatorTable;
import org.polypheny.db.sql.SqlSelect;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.sql.type.SqlTypeUtil;
import org.polypheny.db.sql.validate.OverScope;
import org.polypheny.db.sql.validate.SqlConformance;
import org.polypheny.db.sql.validate.SqlModality;
import org.polypheny.db.sql.validate.SqlValidatorCatalogReader;
import org.polypheny.db.sql.validate.SqlValidatorImpl;
import org.polypheny.db.sql.validate.SqlValidatorNamespace;
import org.polypheny.db.sql.validate.SqlValidatorScope;
import org.polypheny.db.util.Util;
import java.util.HashSet;
import java.util.Set;


/**
 * <code>SqlAdvisorValidator</code> is used by {@link SqlAdvisor} to traverse the parse tree of a SQL statement, not for validation purpose but for setting up the scopes and namespaces
 * to facilitate retrieval of SQL statement completion hints.
 */
public class SqlAdvisorValidator extends SqlValidatorImpl {

    private final Set<SqlValidatorNamespace> activeNamespaces = new HashSet<>();

    private final RelDataType emptyStructType = SqlTypeUtil.createEmptyStructType( typeFactory );


    /**
     * Creates a SqlAdvisor validator.
     *
     * @param opTab Operator table
     * @param catalogReader Catalog reader
     * @param typeFactory Type factory
     * @param conformance Compatibility mode
     */
    public SqlAdvisorValidator( SqlOperatorTable opTab, SqlValidatorCatalogReader catalogReader, RelDataTypeFactory typeFactory, SqlConformance conformance ) {
        super( opTab, catalogReader, typeFactory, conformance );
    }


    /**
     * Registers the identifier and its scope into a map keyed by ParserPosition.
     */
    @Override
    public void validateIdentifier( SqlIdentifier id, SqlValidatorScope scope ) {
        registerId( id, scope );
        try {
            super.validateIdentifier( id, scope );
        } catch ( PolyphenyDbException e ) {
            Util.swallow( e, TRACER );
        }
    }


    private void registerId( SqlIdentifier id, SqlValidatorScope scope ) {
        for ( int i = 0; i < id.names.size(); i++ ) {
            final SqlParserPos subPos = id.getComponentParserPosition( i );
            SqlIdentifier subId =
                    i == id.names.size() - 1
                            ? id
                            : new SqlIdentifier( id.names.subList( 0, i + 1 ), subPos );
            idPositions.put( subPos.toString(), new IdInfo( scope, subId ) );
        }
    }


    @Override
    public SqlNode expand( SqlNode expr, SqlValidatorScope scope ) {
        // Disable expansion. It doesn't help us come up with better hints.
        return expr;
    }


    @Override
    public SqlNode expandOrderExpr( SqlSelect select, SqlNode orderExpr ) {
        // Disable expansion. It doesn't help us come up with better hints.
        return orderExpr;
    }


    /**
     * Calls the parent class method and mask Farrago exception thrown.
     */
    @Override
    public RelDataType deriveType( SqlValidatorScope scope, SqlNode operand ) {
        // REVIEW: Do not mask Error (indicates a serious system problem) or UnsupportedOperationException (a bug). I have to mask UnsupportedOperationException because SqlValidatorImpl.getValidatedNodeType
        // throws it for an unrecognized identifier node I have to mask Error as well because AbstractNamespace.getRowType  called in super.deriveType can do a Util.permAssert that throws Error
        try {
            return super.deriveType( scope, operand );
        } catch ( PolyphenyDbException | UnsupportedOperationException | Error e ) {
            return unknownType;
        }
    }


    // we do not need to validate from clause for traversing the parse tree because there is no SqlIdentifier in from clause that need to be registered into {@link #idPositions} map
    @Override
    protected void validateFrom( SqlNode node, RelDataType targetRowType, SqlValidatorScope scope ) {
        try {
            super.validateFrom( node, targetRowType, scope );
        } catch ( PolyphenyDbException e ) {
            Util.swallow( e, TRACER );
        }
    }


    /**
     * Calls the parent class method and masks Farrago exception thrown.
     */
    @Override
    protected void validateWhereClause( SqlSelect select ) {
        try {
            super.validateWhereClause( select );
        } catch ( PolyphenyDbException e ) {
            Util.swallow( e, TRACER );
        }
    }


    /**
     * Calls the parent class method and masks Farrago exception thrown.
     */
    @Override
    protected void validateHavingClause( SqlSelect select ) {
        try {
            super.validateHavingClause( select );
        } catch ( PolyphenyDbException e ) {
            Util.swallow( e, TRACER );
        }
    }


    @Override
    protected void validateOver( SqlCall call, SqlValidatorScope scope ) {
        try {
            final OverScope overScope = (OverScope) getOverScope( call );
            final SqlNode relation = call.operand( 0 );
            validateFrom( relation, unknownType, scope );
            final SqlNode window = call.operand( 1 );
            SqlValidatorScope opScope = scopes.get( relation );
            if ( opScope == null ) {
                opScope = overScope;
            }
            validateWindow( window, opScope, null );
        } catch ( PolyphenyDbException e ) {
            Util.swallow( e, TRACER );
        }
    }


    @Override
    protected void validateNamespace( final SqlValidatorNamespace namespace, RelDataType targetRowType ) {
        // Only attempt to validate each namespace once. Otherwise if validation fails, we may end up cycling.
        if ( activeNamespaces.add( namespace ) ) {
            super.validateNamespace( namespace, targetRowType );
        } else {
            namespace.setType( emptyStructType );
        }
    }


    @Override
    public boolean validateModality( SqlSelect select, SqlModality modality, boolean fail ) {
        return true;
    }


    @Override
    protected boolean shouldAllowOverRelation() {
        return true; // no reason not to be lenient
    }
}


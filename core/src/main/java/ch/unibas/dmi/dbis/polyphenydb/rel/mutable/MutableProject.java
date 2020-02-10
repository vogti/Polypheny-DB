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

package ch.unibas.dmi.dbis.polyphenydb.rel.mutable;


import ch.unibas.dmi.dbis.polyphenydb.rel.core.Project;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.validate.SqlValidatorUtil;
import ch.unibas.dmi.dbis.polyphenydb.util.Litmus;
import ch.unibas.dmi.dbis.polyphenydb.util.Pair;
import ch.unibas.dmi.dbis.polyphenydb.util.mapping.Mappings;
import java.util.List;
import java.util.Objects;


/**
 * Mutable equivalent of {@link ch.unibas.dmi.dbis.polyphenydb.rel.core.Project}.
 */
public class MutableProject extends MutableSingleRel {

    public final List<RexNode> projects;


    private MutableProject( RelDataType rowType, MutableRel input, List<RexNode> projects ) {
        super( MutableRelType.PROJECT, rowType, input );
        this.projects = projects;
        assert RexUtil.compatibleTypes( projects, rowType, Litmus.THROW );
    }


    /**
     * Creates a MutableProject.
     *
     * @param rowType Row type
     * @param input Input relational expression
     * @param projects List of expressions for the input columns
     */
    public static MutableProject of( RelDataType rowType, MutableRel input, List<RexNode> projects ) {
        return new MutableProject( rowType, input, projects );
    }


    /**
     * Creates a MutableProject.
     *
     * @param input Input relational expression
     * @param exprList List of expressions for the input columns
     * @param fieldNameList Aliases of the expressions, or null to generate
     */
    public static MutableRel of( MutableRel input, List<RexNode> exprList, List<String> fieldNameList ) {
        final RelDataType rowType = RexUtil.createStructType( input.cluster.getTypeFactory(), exprList, fieldNameList, SqlValidatorUtil.F_SUGGESTER );
        return of( rowType, input, exprList );
    }


    @Override
    public boolean equals( Object obj ) {
        return obj == this
                || obj instanceof MutableProject
                && PAIRWISE_STRING_EQUIVALENCE.equivalent( projects, ((MutableProject) obj).projects )
                && input.equals( ((MutableProject) obj).input );
    }


    @Override
    public int hashCode() {
        return Objects.hash( input, PAIRWISE_STRING_EQUIVALENCE.hash( projects ) );
    }


    @Override
    public StringBuilder digest( StringBuilder buf ) {
        return buf.append( "Project(projects: " ).append( projects ).append( ")" );
    }


    /**
     * Returns a list of (expression, name) pairs.
     */
    public final List<Pair<RexNode, String>> getNamedProjects() {
        return Pair.zip( projects, rowType.getFieldNames() );
    }


    public Mappings.TargetMapping getMapping() {
        return Project.getMapping( input.rowType.getFieldCount(), projects );
    }


    @Override
    public MutableRel clone() {
        return of( rowType, input.clone(), projects );
    }
}


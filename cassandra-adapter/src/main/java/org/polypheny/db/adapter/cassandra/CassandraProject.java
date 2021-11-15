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

package org.polypheny.db.adapter.cassandra;


import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.select.Selector;
import com.datastax.oss.driver.api.querybuilder.term.Term;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.polypheny.db.adapter.cassandra.rules.CassandraRules;
import org.polypheny.db.adapter.cassandra.util.CassandraTypesUtils;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.jdbc.JavaTypeFactoryImpl;
import org.polypheny.db.languages.sql.fun.SqlArrayValueConstructor;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.plan.RelOptCost;
import org.polypheny.db.plan.RelOptPlanner;
import org.polypheny.db.plan.RelTraitSet;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.core.Project;
import org.polypheny.db.rel.metadata.RelMetadataQuery;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.rel.type.RelDataTypeField;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexInputRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.util.Pair;


/**
 * Implementation of {@link Project} relational expression in Cassandra.
 */
public class CassandraProject extends Project implements CassandraRel {

    private final boolean arrayValueProject;


    public CassandraProject( RelOptCluster cluster, RelTraitSet traitSet, RelNode input, List<? extends RexNode> projects, RelDataType rowType, boolean arrayValueProject ) {
        super( cluster, traitSet, input, projects, rowType );
        this.arrayValueProject = arrayValueProject;
        // TODO JS: Check this
//        assert getConvention() == CassandraRel.CONVENTION;
//        assert getConvention() == input.getConvention();
    }


    @Override
    public Project copy( RelTraitSet traitSet, RelNode input, List<RexNode> projects, RelDataType rowType ) {
        // TODO js(knn): array value project stuff double check?
        return new CassandraProject( getCluster(), traitSet, input, projects, rowType, this.arrayValueProject );
    }


    @Override
    public RelOptCost computeSelfCost( RelOptPlanner planner, RelMetadataQuery mq ) {
        return super.computeSelfCost( planner, mq ).multiplyBy( 0.8 );
    }


    @Override
    public void implement( CassandraImplementContext context ) {

        if ( arrayValueProject ) {
            final RelDataTypeFactory typeFactory = getCluster().getTypeFactory();
            RelDataType rowType = context.cassandraTable.getRowType( typeFactory );

            List<Pair<String, String>> pairs = Pair.zip( rowType.getFieldList().stream().map( RelDataTypeField::getPhysicalName ).collect( Collectors.toList() ), rowType.getFieldNames() );
            Map<String, String> nameMapping = new HashMap<>();
            for ( Pair<String, String> pair : pairs ) {
                nameMapping.put( pair.right, pair.left );
            }

            List<String> cassandraFieldNames = getRowType().getFieldNames();
            List<String> cassPhysicalFields = new ArrayList<>();
            for ( String fieldName : cassandraFieldNames ) {
                cassPhysicalFields.add( nameMapping.get( fieldName ) );
            }

            // Yes I am literally copying what the values implementation is doing
            final List<RelDataTypeField> physicalFields = context.cassandraTable.getRowType( new JavaTypeFactoryImpl() ).getFieldList();
            final List<RelDataTypeField> logicalFields = this.rowType.getFieldList();
            final List<RelDataTypeField> fields = new ArrayList<>();
            for ( RelDataTypeField field : logicalFields ) {
                for ( RelDataTypeField physicalField : physicalFields ) {
                    if ( field.getName().equals( physicalField.getName() ) ) {
                        fields.add( physicalField );
                        break;
                    }
                }
            }

            Map<String, Term> oneInsert = new LinkedHashMap<>();
            List<Pair<RexNode, String>> namedProjects = getNamedProjects();
            for ( int i = 0; i < namedProjects.size(); i++ ) {
                Pair<RexNode, String> pair = namedProjects.get( i );
                final String originalName = cassPhysicalFields.get( i );
//                final String originalName = pair.left.accept( translator );
                if ( pair.left instanceof RexLiteral ) {
                    // Normal literal value
                    final String name = pair.right;
                    oneInsert.put( originalName, QueryBuilder.literal( CassandraValues.literalValue( (RexLiteral) pair.left ) ) );
                } else if ( pair.left instanceof RexCall ) {
                    SqlArrayValueConstructor arrayValueConstructor = (SqlArrayValueConstructor) ((RexCall) pair.left).op;
                    UdtValue udtValue = CassandraTypesUtils.createArrayContainerDataType(
                            context.cassandraTable.getUnderlyingConvention().arrayContainerUdt,
                            arrayValueConstructor.dimension,
                            arrayValueConstructor.maxCardinality,
                            ((RexCall) pair.left).type.getComponentType().getPolyType(),
                            (RexCall) pair.left );
                    String udtString = udtValue.getFormattedContents();
                    Term udtTerm = QueryBuilder.raw( udtString );
                    oneInsert.put( originalName, udtTerm );
                }
            }

            List<Map<String, Term>> valuesList = new ArrayList<>();
            valuesList.add( oneInsert );
            context.addInsertValues( valuesList );
        } else {
            context.visitChild( 0, getInput() );
            final CassandraRules.RexToCassandraTranslator translator = new CassandraRules.RexToCassandraTranslator(
                    (JavaTypeFactory) getCluster().getTypeFactory(),
                    CassandraRules.cassandraPhysicalFieldNames( getInput().getRowType() ) );
            final List<Selector> fields = new ArrayList<>();
            for ( Pair<RexNode, String> pair : getNamedProjects() ) {
                if ( pair.left instanceof RexInputRef ) {
                    String name = pair.right;
//                getRowType()
//                ((RexInputRef) pair.left);
                    final String originalName = pair.left.accept( translator );
                    if ( name.startsWith( "_" ) ) {
                        name = "\"" + name + "\"";
                    }
                    fields.add( Selector.column( originalName ).as( name ) );
                }
            }
            context.addSelectColumns( fields );
        }
    }

}


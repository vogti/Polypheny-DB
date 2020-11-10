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
 */

package org.polypheny.db.adapter.cottontail.rules;


import java.util.List;
import org.polypheny.db.adapter.cottontail.CottontailConvention;
import org.polypheny.db.adapter.cottontail.CottontailToEnumerableConverter;
import org.polypheny.db.adapter.cottontail.rel.CottontailSortAndProject;
import org.polypheny.db.adapter.cottontail.rel.SortAndProject;
import org.polypheny.db.plan.Convention;
import org.polypheny.db.plan.RelOptRule;
import org.polypheny.db.plan.RelOptRuleCall;
import org.polypheny.db.plan.RelTraitSet;
import org.polypheny.db.plan.volcano.RelSubset;
import org.polypheny.db.rel.RelFieldCollation;
import org.polypheny.db.rel.RelFieldCollation.Direction;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.core.Project;
import org.polypheny.db.rel.core.Sort;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeField;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexDynamicParam;
import org.polypheny.db.rex.RexInputRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.sql.fun.SqlArrayValueConstructor;
import org.polypheny.db.sql.fun.SqlKnnFunction;
import org.polypheny.db.tools.RelBuilderFactory;
import org.polypheny.db.type.PolyType;


public class CottontailSortAndProjectRule extends RelOptRule {

    protected final Convention out;

    CottontailSortAndProjectRule( CottontailConvention out, RelBuilderFactory relBuilderFactory  ) {
        super( operand( Sort.class, operand( Project.class, any() ) ), relBuilderFactory, "CottontailSortAndProjectRule" + out.getName() );
        this.out = out;
    }

    @Override
    public boolean matches( RelOptRuleCall call ) {
        final Sort sort = call.rel( 0 );

        if ( !( call.rel( 1 ) instanceof Project ) ) {
            return false;
        }

        Project project = call.rel( 1 );

        // Projection checks
        Project innerProject = CottontailSortRule.getUnderlyingProject( (RelSubset) project.getInput(), this.out );

        if ( innerProject != null ) {
            return false;
        }

        boolean containsInputRefs = false;
        boolean containsValueProjects = false;
        boolean foundKnnFunction = false;
        int knnColumn = -1;

        List<RexNode> projects = project.getProjects();
        for ( int i = 0, projectsSize = projects.size(); i < projectsSize; i++ ) {
            RexNode e = projects.get( i );

            if ( e instanceof RexInputRef ) {
                containsInputRefs = true;
            } else if ( (e instanceof RexLiteral) || (e instanceof RexDynamicParam) || ((e instanceof RexCall) && (((RexCall) e).getOperator() instanceof SqlArrayValueConstructor)) ) {
                containsValueProjects = true;
            } else if ( (e instanceof RexCall) && (((RexCall) e).getOperator() instanceof SqlKnnFunction) ) {
                RexCall rexCall = (RexCall) e;
                if ( !foundKnnFunction ) {

                    if ( (CottontailToEnumerableConverter.SUPPORTED_ARRAY_COMPONENT_TYPES.contains( rexCall.getOperands().get( 0 ).getType().getComponentType().getPolyType() ))) {
                        foundKnnFunction = true;
                        knnColumn = i;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        if (!(containsInputRefs && foundKnnFunction && !containsValueProjects)) {
            return false;
        }


        // Sort checks
        if ( sort.getCollation().getFieldCollations().size() != 1 ) {
            return false;
        }

        RelFieldCollation collation = sort.getCollation().getFieldCollations().get( 0 );

        if ( collation.getFieldIndex() != knnColumn ) {
            return false;
        }

        return collation.getDirection() == Direction.ASCENDING;
    }


    @Override
    public void onMatch( RelOptRuleCall call ) {
        final Sort sort = call.rel( 0 );
        Project project = call.rel( 1 );

        final RelTraitSet traitSet = sort.getTraitSet().replace( out );
        final RelNode input;
        final RelTraitSet inputTraitSet = project.getInput().getTraitSet().replace( out );
        input = convert( project.getInput(), inputTraitSet );

        boolean arrayValueProject = true;
        for ( RexNode e : project.getProjects() ) {
            if ( !((e instanceof RexCall) && (((RexCall) e).getOperator() instanceof SqlArrayValueConstructor)) && !(e instanceof RexLiteral) && !(e instanceof RexDynamicParam) ) {
                arrayValueProject = false;
            }
        }

        SortAndProject sortAndProject = new CottontailSortAndProject(
                sort.getCluster(),
                traitSet,
                input,
                sort.getCollation(),
                sort.offset,
                sort.fetch,
                project.getProjects(),
                project.getRowType(),
                null,
                arrayValueProject );

        call.transformTo( sortAndProject );
    }
}

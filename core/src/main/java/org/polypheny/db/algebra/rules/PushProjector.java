/*
 * Copyright 2019-2024 The Polypheny Project
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

package org.polypheny.db.algebra.rules;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.calcite.linq4j.Ord;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.constant.SemiJoinType;
import org.polypheny.db.algebra.core.Correlate;
import org.polypheny.db.algebra.core.Join;
import org.polypheny.db.algebra.core.Project;
import org.polypheny.db.algebra.core.SemiJoin;
import org.polypheny.db.algebra.core.SetOp;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.nodes.Operator;
import org.polypheny.db.plan.AlgOptUtil;
import org.polypheny.db.plan.Strong;
import org.polypheny.db.rex.RexBuilder;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.rex.RexUtil;
import org.polypheny.db.rex.RexVisitorImpl;
import org.polypheny.db.tools.AlgBuilder;
import org.polypheny.db.util.BitSets;
import org.polypheny.db.util.ImmutableBitSet;
import org.polypheny.db.util.Pair;


/**
 * PushProjector is a utility class used to perform operations used in push projection rules.
 *
 * Pushing is particularly interesting in the case of join, because there are multiple inputs. Generally an expression can be pushed down to a particular input if it depends upon no other inputs. If it can be pushed
 * down to both sides, it is pushed down to the left.
 *
 * Sometimes an expression needs to be split before it can be pushed down.
 * To flag that an expression cannot be split, specify a rule that it must be <dfn>preserved</dfn>. Such an expression will be pushed down intact to one of the inputs, or not pushed down at all.
 */
public class PushProjector {

    private final Project origProj;
    private final RexNode origFilter;
    private final AlgNode childRel;
    private final ExprCondition preserveExprCondition;
    private final AlgBuilder algBuilder;

    /**
     * Original projection expressions
     */
    final List<RexNode> origProjExprs;

    /**
     * Fields from the {@link AlgNode} that the projection is being pushed past
     */
    final List<AlgDataTypeField> childFields;

    /**
     * Number of fields in the {@link AlgNode} that the projection is being pushed past
     */
    final int nChildFields;

    /**
     * Bitmap containing the references in the original projection
     */
    final BitSet projRefs;

    /**
     * Bitmap containing the fields in the {@link AlgNode} that the projection is being pushed past, if the {@link AlgNode} is not a join. If the {@link AlgNode} is a join, then the fields correspond to the left hand side of the join.
     */
    final ImmutableBitSet childBitmap;

    /**
     * Bitmap containing the fields in the right hand side of a join, in the case where the projection is being pushed past a join. Not used otherwise.
     */
    final ImmutableBitSet rightBitmap;

    /**
     * Bitmap containing the fields that should be strong, i.e. when preserving expressions we can only preserve them if the expressions if it is null when these fields are null.
     */
    final ImmutableBitSet strongBitmap;

    /**
     * Number of fields in the {@link AlgNode} that the projection is being pushed past, if the {@link AlgNode} is not a join. If the {@link AlgNode} is a join, then this is the number of fields in the left hand side of the join.
     *
     * The identity {@code nChildFields == nSysFields + nFields + nFieldsRight} holds. {@code nFields} does not include {@code nSysFields}.
     * The output of a join looks like this:
     *
     * <blockquote><pre>
     * | nSysFields | nFields | nFieldsRight |
     * </pre></blockquote>
     *
     * The output of a single-input alg looks like this:
     *
     * <blockquote><pre>
     * | nSysFields | nFields |
     * </pre></blockquote>
     */
    final int nFields;

    /**
     * Number of fields in the right hand side of a join, in the case where the projection is being pushed past a join. Always 0 otherwise.
     */
    final int nFieldsRight;


    /**
     * Expressions referenced in the projection/filter that should be preserved.
     * In the case where the projection is being pushed past a join, then the list only contains the expressions corresponding to the left hand side of the join.
     */
    final List<RexNode> childPreserveExprs;

    /**
     * Expressions referenced in the projection/filter that should be preserved, corresponding to expressions on the right hand side of the join, if the projection is being pushed past a join. Empty list otherwise.
     */
    final List<RexNode> rightPreserveExprs;

    /**
     * Number of system fields being projected.
     */
    int nSystemProject;

    /**
     * Number of fields being projected. In the case where the projection is being pushed past a join, the number of fields being projected from the left hand side of the join.
     */
    int nProject;

    /**
     * Number of fields being projected from the right hand side of a join, in the case where the projection is being pushed past a join. 0 otherwise.
     */
    int nRightProject;

    /**
     * Rex builder used to create new expressions.
     */
    final RexBuilder rexBuilder;


    /**
     * Creates a PushProjector object for pushing projects past a AlgNode.
     *
     * @param origProj the original projection that is being pushed; may be null if the projection is implied as a result of a projection having been trivially removed
     * @param origFilter the filter that the projection must also be pushed past, if applicable
     * @param childRel the {@link AlgNode} that the projection is being pushed past
     * @param preserveExprCondition condition for whether an expression should be preserved in the projection
     */
    public PushProjector( Project origProj, RexNode origFilter, AlgNode childRel, ExprCondition preserveExprCondition, AlgBuilder algBuilder ) {
        this.origProj = origProj;
        this.origFilter = origFilter;
        this.childRel = childRel;
        this.preserveExprCondition = preserveExprCondition;
        this.algBuilder = Objects.requireNonNull( algBuilder );
        if ( origProj == null ) {
            origProjExprs = ImmutableList.of();
        } else {
            origProjExprs = origProj.getProjects();
        }

        childFields = childRel.getTupleType().getFields();
        nChildFields = childFields.size();

        projRefs = new BitSet( nChildFields );
        if ( childRel instanceof Join joinRel ) {
            List<AlgDataTypeField> leftFields = joinRel.getLeft().getTupleType().getFields();
            List<AlgDataTypeField> rightFields = joinRel.getRight().getTupleType().getFields();
            nFields = leftFields.size();
            nFieldsRight = childRel instanceof SemiJoin ? 0 : rightFields.size();
            childBitmap = ImmutableBitSet.range( 0, nFields );
            rightBitmap = ImmutableBitSet.range( nFields, nChildFields );

            strongBitmap = switch ( joinRel.getJoinType() ) {
                case INNER -> ImmutableBitSet.of();
                case RIGHT -> ImmutableBitSet.range( 0, nFields );// All the left-input's columns must be strong
                case LEFT -> ImmutableBitSet.range( nFields, nChildFields );// All the right-input's columns must be strong
                case FULL -> ImmutableBitSet.range( 0, nChildFields );
            };

        } else if ( childRel instanceof Correlate corrAlg ) {
            List<AlgDataTypeField> leftFields = corrAlg.getLeft().getTupleType().getFields();
            List<AlgDataTypeField> rightFields = corrAlg.getRight().getTupleType().getFields();
            nFields = leftFields.size();
            SemiJoinType joinType = corrAlg.getJoinType();
            switch ( joinType ) {
                case SEMI:
                case ANTI:
                    nFieldsRight = 0;
                    break;
                default:
                    nFieldsRight = rightFields.size();
            }
            childBitmap = ImmutableBitSet.range( 0, nFields );
            rightBitmap = ImmutableBitSet.range( nFields, nChildFields );

            // Required columns need to be included in project
            projRefs.or( BitSets.of( corrAlg.getRequiredColumns() ) );

            strongBitmap = switch ( joinType ) {
                case INNER -> ImmutableBitSet.of();
                case ANTI, SEMI -> ImmutableBitSet.range( 0, nFields ); // All the left-input's columns must be strong
                case LEFT -> ImmutableBitSet.range( nFields, nChildFields ); // All the right-input's columns must be strong
            };
        } else {
            nFields = nChildFields;
            nFieldsRight = 0;
            childBitmap = ImmutableBitSet.range( nChildFields );
            rightBitmap = null;
            strongBitmap = ImmutableBitSet.of();
        }
        assert nChildFields == nFields + nFieldsRight;

        childPreserveExprs = new ArrayList<>();
        rightPreserveExprs = new ArrayList<>();

        rexBuilder = childRel.getCluster().getRexBuilder();
    }


    /**
     * Decomposes a projection to the input references referenced by a projection and a filter, either of which is optional. If both are provided, the filter is underneath the project.
     *
     * Creates a projection containing all input references as well as preserving any special expressions. Converts the original projection and/or filter to reference the new projection. Then, finally puts on top,
     * a final projection corresponding to the original projection.
     *
     * @param defaultExpr expression to be used in the projection if no fields or special columns are selected
     * @return the converted projection if it makes sense to push elements of the projection; otherwise returns null
     */
    public AlgNode convertProject( RexNode defaultExpr ) {
        // locate all fields referenced in the projection and filter
        locateAllRefs();

        // If all columns are being selected (either explicitly in the projection) or via a "select *", then there needs to be some special expressions to preserve in the projection; otherwise,
        // there's no point in proceeding any further
        if ( origProj == null ) {
            if ( childPreserveExprs.size() == 0 ) {
                return null;
            }

            // Even though there is no projection, this is the same as selecting all fields
            if ( nChildFields > 0 ) {
                // Calling with nChildFields == 0 should be safe but hits
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6222207
                projRefs.set( 0, nChildFields );
            }
            nProject = nChildFields;
        } else if ( (projRefs.cardinality() == nChildFields) && (childPreserveExprs.size() == 0) ) {
            return null;
        }

        // If nothing is being selected from the underlying alg, just project the default expression passed in as a parameter or the first column if there is no default expression
        if ( (projRefs.cardinality() == 0) && (childPreserveExprs.size() == 0) ) {
            if ( defaultExpr != null ) {
                childPreserveExprs.add( defaultExpr );
            } else if ( nChildFields == 1 ) {
                return null;
            } else {
                projRefs.set( 0 );
                nProject = 1;
            }
        }

        // Create a new projection referencing all fields referenced in either the project or the filter
        AlgNode newProject = createProjectRefsAndExprs( childRel, false, false );

        int[] adjustments = getAdjustments();

        // If a filter was passed in, convert it to reference the projected columns, placing it on top of the project just created
        AlgNode projChild;
        if ( origFilter != null ) {
            RexNode newFilter = convertRefsAndExprs( origFilter, newProject.getTupleType().getFields(), adjustments );
            algBuilder.push( newProject );
            algBuilder.filter( newFilter );
            projChild = algBuilder.build();
        } else {
            projChild = newProject;
        }

        // Put the original project on top of the filter/project, converting it to reference the modified projection list; otherwise, create a projection that essentially selects all fields
        return createNewProject( projChild, adjustments );
    }


    /**
     * Locates all references found in either the projection expressions a filter, as well as references to expressions that should be preserved. Based on that, determines whether pushing the projection makes sense.
     *
     * @return true if all inputs from the child that the projection is being pushed past are referenced in the projection/filter and no special preserve expressions are referenced; in that case, it does not make sense to push the projection
     */
    public boolean locateAllRefs() {
        RexUtil.apply(
                new InputSpecialOpFinder(
                        projRefs,
                        childBitmap,
                        rightBitmap,
                        strongBitmap,
                        preserveExprCondition,
                        childPreserveExprs,
                        rightPreserveExprs ),
                origProjExprs,
                origFilter );

        projRefs.set(
                nFields,
                nFields,
                true );

        // Count how many fields are projected.
        nSystemProject = 0;
        nProject = 0;
        nRightProject = 0;
        for ( int bit : BitSets.toIter( projRefs ) ) {
            if ( bit < nFields ) {
                nProject++;
            } else {
                nRightProject++;
            }
        }

        assert nSystemProject + nProject + nRightProject == projRefs.cardinality();

        if ( (childRel instanceof Join) || (childRel instanceof SetOp) ) {
            // If nothing is projected from the children, arbitrarily project the first columns; this is necessary since Fennel doesn't handle 0-column projections
            if ( (nProject == 0) && (childPreserveExprs.size() == 0) ) {
                projRefs.set( 0 );
                nProject = 1;
            }
            if ( childRel instanceof Join ) {
                if ( (nRightProject == 0) && (rightPreserveExprs.size() == 0) ) {
                    projRefs.set( nFields );
                    nRightProject = 1;
                }
            }
        }

        // No need to push projections if all children fields are being referenced and there are no special preserve expressions; note that we need to do this check after we've handled the 0-column project cases
        if ( projRefs.cardinality() == nChildFields
                && childPreserveExprs.size() == 0
                && rightPreserveExprs.size() == 0 ) {
            return true;
        }

        return false;
    }


    /**
     * Creates a projection based on the inputs specified in a bitmap and the expressions that need to be preserved. The expressions are appended after the input references.
     *
     * @param projChild child that the projection will be created on top of
     * @param adjust if true, need to create new projection expressions; otherwise, the existing ones are reused
     * @param rightSide if true, creating a projection for the right hand side of a join
     * @return created projection
     */
    public Project createProjectRefsAndExprs( AlgNode projChild, boolean adjust, boolean rightSide ) {
        List<RexNode> preserveExprs;
        int nInputRefs;
        int offset;

        if ( rightSide ) {
            preserveExprs = rightPreserveExprs;
            nInputRefs = nRightProject;
            offset = nFields;
        } else {
            preserveExprs = childPreserveExprs;
            nInputRefs = nProject;
            offset = 0;
        }
        int refIdx = offset - 1;
        List<Pair<RexNode, String>> newProjects = new ArrayList<>();
        List<AlgDataTypeField> destFields = projChild.getTupleType().getFields();

        // add on the input references
        for ( int i = 0; i < nInputRefs; i++ ) {
            refIdx = projRefs.nextSetBit( refIdx + 1 );
            assert refIdx >= 0;
            final AlgDataTypeField destField = destFields.get( refIdx - offset );
            newProjects.add(
                    Pair.of(
                            (RexNode) rexBuilder.makeInputRef( destField.getType(), refIdx - offset ),
                            destField.getName() ) );
        }

        // Add on the expressions that need to be preserved, converting the arguments to reference the projected columns (if necessary)
        int[] adjustments = {};
        if ( (preserveExprs.size() > 0) && adjust ) {
            adjustments = new int[childFields.size()];
            for ( int idx = offset; idx < childFields.size(); idx++ ) {
                adjustments[idx] = -offset;
            }
        }
        for ( RexNode projExpr : preserveExprs ) {
            RexNode newExpr;
            if ( adjust ) {
                newExpr =
                        projExpr.accept(
                                new AlgOptUtil.RexInputConverter(
                                        rexBuilder,
                                        childFields,
                                        destFields,
                                        adjustments ) );
            } else {
                newExpr = projExpr;
            }
            newProjects.add( Pair.of( newExpr, ((RexCall) projExpr).getOperator().getName() ) );
        }

        return (Project) algBuilder.push( projChild )
                .projectNamed( Pair.left( newProjects ), Pair.right( newProjects ), true )
                .build();
    }


    /**
     * Determines how much each input reference needs to be adjusted as a result of projection
     *
     * @return array indicating how much each input needs to be adjusted by
     */
    public int[] getAdjustments() {
        int[] adjustments = new int[nChildFields];
        int newIdx = 0;
        int rightOffset = childPreserveExprs.size();
        for ( int pos : BitSets.toIter( projRefs ) ) {
            adjustments[pos] = -(pos - newIdx);
            if ( pos >= nFields ) {
                adjustments[pos] += rightOffset;
            }
            newIdx++;
        }
        return adjustments;
    }


    /**
     * Clones an expression tree and walks through it, adjusting each RexInputRef index by some amount, and converting expressions that need to be preserved to field references.
     *
     * @param rex the expression
     * @param destFields fields that the new expressions will be referencing
     * @param adjustments the amount each input reference index needs to be adjusted by
     * @return modified expression tree
     */
    public RexNode convertRefsAndExprs( RexNode rex, List<AlgDataTypeField> destFields, int[] adjustments ) {
        return rex.accept(
                new RefAndExprConverter(
                        rexBuilder,
                        childFields,
                        destFields,
                        adjustments,
                        childPreserveExprs,
                        nProject,
                        rightPreserveExprs,
                        nProject + childPreserveExprs.size() + nRightProject ) );
    }


    /**
     * Creates a new projection based on the original projection, adjusting all input refs using an adjustment array passed in. If there was no original projection, create a new one that selects every field from the underlying alg.
     *
     * If the resulting projection would be trivial, return the child.
     *
     * @param projChild child of the new project
     * @param adjustments array indicating how much each input reference should be adjusted by
     * @return the created projection
     */
    public AlgNode createNewProject( AlgNode projChild, int[] adjustments ) {
        final List<Pair<RexNode, String>> projects = new ArrayList<>();

        if ( origProj != null ) {
            for ( Pair<RexNode, String> p : origProj.getNamedProjects() ) {
                projects.add( Pair.of( convertRefsAndExprs( p.left, projChild.getTupleType().getFields(), adjustments ), p.right ) );
            }
        } else {
            for ( Ord<AlgDataTypeField> field : Ord.zip( childFields ) ) {
                projects.add( Pair.of( rexBuilder.makeInputRef( field.e.getType(), field.i ), field.e.getName() ) );
            }
        }
        return algBuilder.push( projChild )
                .project( Pair.left( projects ), Pair.right( projects ) )
                .build();
    }


    /**
     * Visitor which builds a bitmap of the inputs used by an expressions, as well as locating expressions corresponding to special operators.
     */
    private class InputSpecialOpFinder extends RexVisitorImpl<Void> {

        private final BitSet rexRefs;
        private final ImmutableBitSet leftFields;
        private final ImmutableBitSet rightFields;
        private final ImmutableBitSet strongFields;
        private final ExprCondition preserveExprCondition;
        private final List<RexNode> preserveLeft;
        private final List<RexNode> preserveRight;
        private final Strong strong;


        InputSpecialOpFinder( BitSet rexRefs, ImmutableBitSet leftFields, ImmutableBitSet rightFields, final ImmutableBitSet strongFields, ExprCondition preserveExprCondition, List<RexNode> preserveLeft, List<RexNode> preserveRight ) {
            super( true );
            this.rexRefs = rexRefs;
            this.leftFields = leftFields;
            this.rightFields = rightFields;
            this.preserveExprCondition = preserveExprCondition;
            this.preserveLeft = preserveLeft;
            this.preserveRight = preserveRight;

            this.strongFields = strongFields;
            this.strong = Strong.of( strongFields );
        }


        @Override
        public Void visitCall( RexCall call ) {
            if ( preserve( call ) ) {
                return null;
            }
            super.visitCall( call );
            return null;
        }


        private boolean isStrong( final ImmutableBitSet exprArgs, final RexNode call ) {
            // If the expressions do not use any of the inputs that require output to be null, no need to check.  Otherwise, check that the expression is null.
            // For example, in an "left outer join", we don't require that expressions pushed down into the left input to be strong.  On the other hand, expressions pushed into the right input must be.
            // In that case, strongFields == right input fields.
            return !strongFields.intersects( exprArgs ) || strong.isNull( call );
        }


        private boolean preserve( RexNode call ) {
            if ( preserveExprCondition.test( call ) ) {
                // If the arguments of the expression only reference the left hand side, preserve it on the left; similarly, if it only references expressions on the right
                final ImmutableBitSet exprArgs = AlgOptUtil.InputFinder.bits( call );
                if ( exprArgs.cardinality() > 0 ) {
                    if ( leftFields.contains( exprArgs ) && isStrong( exprArgs, call ) ) {
                        if ( !preserveLeft.contains( call ) ) {
                            preserveLeft.add( call );
                        }
                        return true;
                    } else if ( rightFields.contains( exprArgs ) && isStrong( exprArgs, call ) ) {
                        assert preserveRight != null;
                        if ( !preserveRight.contains( call ) ) {
                            preserveRight.add( call );
                        }
                        return true;
                    }
                }
                // If the expression arguments reference both the left and right, fall through and don't attempt to preserve the expression, but instead locate references and special ops in the call operands
            }
            return false;
        }


        @Override
        public Void visitIndexRef( RexIndexRef inputRef ) {
            rexRefs.set( inputRef.getIndex() );
            return null;
        }

    }


    /**
     * Walks an expression tree, replacing input refs with new values to reflect projection and converting special expressions to field references.
     */
    private class RefAndExprConverter extends AlgOptUtil.RexInputConverter {

        private final List<RexNode> preserveLeft;
        private final int firstLeftRef;
        private final List<RexNode> preserveRight;
        private final int firstRightRef;


        RefAndExprConverter( RexBuilder rexBuilder, List<AlgDataTypeField> srcFields, List<AlgDataTypeField> destFields, int[] adjustments, List<RexNode> preserveLeft, int firstLeftRef, List<RexNode> preserveRight, int firstRightRef ) {
            super( rexBuilder, srcFields, destFields, adjustments );
            this.preserveLeft = preserveLeft;
            this.firstLeftRef = firstLeftRef;
            this.preserveRight = preserveRight;
            this.firstRightRef = firstRightRef;
        }


        @Override
        public RexNode visitCall( RexCall call ) {
            // If the expression corresponds to one that needs to be preserved, convert it to a field reference; otherwise, convert the entire expression
            int match = findExprInLists( call, preserveLeft, firstLeftRef, preserveRight, firstRightRef );
            if ( match >= 0 ) {
                return rexBuilder.makeInputRef( destFields.get( match ).getType(), match );
            }
            return super.visitCall( call );
        }


        /**
         * Looks for a matching RexNode from among two lists of RexNodes and returns the offset into the list corresponding to the match, adjusted by an amount, depending on whether the match was from the first or second list.
         *
         * @param rex RexNode that is being matched against
         * @param rexList1 first list of RexNodes
         * @param adjust1 adjustment if match occurred in first list
         * @param rexList2 second list of RexNodes
         * @param adjust2 adjustment if match occurred in the second list
         * @return index in the list corresponding to the matching RexNode; -1 if no match
         */
        private int findExprInLists( RexNode rex, List<RexNode> rexList1, int adjust1, List<RexNode> rexList2, int adjust2 ) {
            int match = rexList1.indexOf( rex );
            if ( match >= 0 ) {
                return match + adjust1;
            }

            if ( rexList2 != null ) {
                match = rexList2.indexOf( rex );
                if ( match >= 0 ) {
                    return match + adjust2;
                }
            }

            return -1;
        }

    }


    /**
     * A functor that replies true or false for a given expression.
     *
     * @see org.polypheny.db.algebra.rules.PushProjector.OperatorExprCondition
     */
    public interface ExprCondition extends Predicate<RexNode> {

        /**
         * Evaluates a condition for a given expression.
         *
         * @param expr Expression
         * @return result of evaluating the condition
         */
        @Override
        boolean test( RexNode expr );

        /**
         * Constant condition that replies {@code false} for all expressions.
         */
        ExprCondition FALSE = expr -> false;

        /**
         * Constant condition that replies {@code true} for all expressions.
         */
        ExprCondition TRUE = expr -> true;

    }


    /**
     * An expression condition that evaluates to true if the expression is a call to one of a set of operators.
     */
    static class OperatorExprCondition implements ExprCondition {

        private final Set<Operator> operatorSet;


        /**
         * Creates an OperatorExprCondition.
         *
         * @param operatorSet Set of operators
         */
        OperatorExprCondition( Iterable<? extends Operator> operatorSet ) {
            this.operatorSet = ImmutableSet.copyOf( operatorSet );
        }


        @Override
        public boolean test( RexNode expr ) {
            return expr instanceof RexCall && operatorSet.contains( ((RexCall) expr).getOperator() );
        }

    }

}


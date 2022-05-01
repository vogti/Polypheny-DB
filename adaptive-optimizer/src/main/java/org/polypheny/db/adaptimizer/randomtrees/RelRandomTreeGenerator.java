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

package org.polypheny.db.adaptimizer.randomtrees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.polypheny.db.adaptimizer.environment.DefaultTestEnvironment;
import org.polypheny.db.adaptimizer.except.AdaptiveOptTreeGenException;
import org.polypheny.db.adaptimizer.except.InvalidBinaryNodeException;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.operators.OperatorName;
import org.polypheny.db.http.model.SortDirection;
import org.polypheny.db.http.model.SortState;
import org.polypheny.db.languages.OperatorRegistry;
import org.polypheny.db.nodes.Operator;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.sql.sql.SqlWriter.Frame;
import org.polypheny.db.tools.AlgBuilder;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Pair;
import org.polypheny.db.util.Util;

/**
 * The {@link RelRandomTreeGenerator} takes a {@link RelRandomTreeTemplate} to generate logical operator trees
 * at random.
 */
@Slf4j
public class RelRandomTreeGenerator implements RelTreeGenerator {

    private final RelRandomTreeTemplate template;

    private final Stack<AdaptiveNode> traceStack;

    private String traceAppendix;

    private int treeCounter;

    @Getter
    private int nodeCounter;

    @Getter
    private long treeGenTime;

    public RelRandomTreeGenerator( RelRandomTreeTemplate template ) {
        this.template = template;
        this.traceStack = new Stack<>();
        this.treeCounter = 0;
    }

    /**
     * Generates a random Logical Operator Tree given the constraints set in the {@link RelRandomTreeTemplate}.
     * Returns null if the generation failed to produce a result that passed validators.
     * @param statement         {@link Statement} created by a transaction.
     * @return                  {@link AlgNode } tree root of the logical operator tree.
     */
    public AlgNode generate( Statement statement ) {
        this.traceAppendix = null;
        this.traceStack.clear();
        this.treeCounter++;
        this.nodeCounter = 0;

        StopWatch stopWatch = new StopWatch();
        AlgNode algNode = null;
        stopWatch.start();
        try {
            algNode = this.generateTreeNode( AlgBuilder.create( statement ), new AdaptiveNode( 0 ) ).build();
        } catch ( AdaptiveOptTreeGenException ignore ) {
            // ignore
        }
        stopWatch.stop();
        this.treeGenTime = stopWatch.getTime();
        return algNode;
    }

    /**
     * Generates a random {@link AdaptiveNode}, and converts it into an {@link AlgNode} using the {@link AlgBuilder}.
     * Calls itself recursively until a maximum depth is reached specified in the {@link RelRandomTreeTemplate} provided
     * to the {@link RelRandomTreeGenerator} in the class constructor.
     */
    private AlgBuilder generateTreeNode( AlgBuilder algBuilder, final AdaptiveNode node ) throws AdaptiveOptTreeGenException {

        if ( this.template.isMaxDepth( node.getDepth() ) ) {

            this.setModeForTableScan( node );

        } else {

            // Recursively call generateTreeNode on next children...
            if ( this.template.nextOperatorIsUnary() ) {
                AdaptiveNode left = new AdaptiveNode( node.getDepth() + 1 );

                node.children = new AdaptiveNode[]{ left, null };
                node.inputCount = 1;

                this.generateTreeNode( algBuilder, left );

                this.setModeForUnaryOperatorNode( node );

            } else {
                AdaptiveNode left = new AdaptiveNode( node.getDepth() + 1 );
                AdaptiveNode right = new AdaptiveNode( node.getDepth() + 1 );

                node.children = new AdaptiveNode[]{ left, right };
                node.inputCount = 2;

                this.generateTreeNode( algBuilder, left );
                this.generateTreeNode( algBuilder, right );

                this.setModeForBinaryOperatorNode( algBuilder, node );

            }

        }

        this.nodeCounter++;
        this.traceStack.push( node );
        return this.convertNode( algBuilder, node );

    }


    /**
     * Configures a random binary operator node and handles edge-cases.
     */
    private void setModeForBinaryOperatorNode( AlgBuilder algBuilder, final AdaptiveNode node ) throws AdaptiveOptTreeGenException {

        node.type = this.template.nextBinaryOperatorType();

        AdaptiveTableRecord left = node.children[ 0 ].getAdaptiveTableRecord();
        AdaptiveTableRecord right = node.children[ 1 ].getAdaptiveTableRecord();

        boolean recurse = false;
        switch ( node.type ) {
            // Set Operations
            case "Union":
            case "Intersect":
            case "Minus":
                // Check if Set operation Possible
                if ( this.template.haveSamePolyTypes( left, right ) ) {
                    // Set Operation possible

                    node.all = this.template.nextAll();
                    node.setAdaptiveTableRecord( AdaptiveTableRecord.from( left ) );

                } else if ( this.template.switchToJoinIfSetOpNotViable() ) {
                    // Switch to join
                    node.type = "Join";
                    recurse = true;
                } else {
                    // We try to alter the tree such that the Set Operation is Valid

//                    if ( log.isDebugEnabled() ) {
//                        log.debug( "Generating projections to account for invalid set operation for set operation..." );
//                        log.debug( "Old columns...  left: {},  right: {}", left.getColumns(), right.getColumns()  );
//                    }

                    // Insert Projections copying records from children left and right
                    AdaptiveNode.insertProjections( node );


                    // Get new left and right records
                    left =  node.children[ 0 ].getAdaptiveTableRecord();
                    right = node.children[ 1 ].getAdaptiveTableRecord();

//                    if ( log.isDebugEnabled() ) {
//                        log.debug( "Projections added to tree... left: {},  right: {}", left.getColumns(), right.getColumns() );
//                    }


                    // Match on column type subsets
                    this.template.matchOnColumnTypeSubsets(
                            left,
                            right
                    );

//                    if ( log.isDebugEnabled() ) {
//                        log.debug( "Projections changed accordingly... left: {},  right: {}", left.getColumns(), right.getColumns() );
//                    }

                    // Check that they are not empty
                    if ( left.getColumns().isEmpty() ) {
                        // There were no matching PolyTypes
                        if ( log.isDebugEnabled() ) {
                            //log.debug( "No matching PolyTypes found, trying extensions..." );
                        }

                        // Reset Records
                        node.children[ 0 ].resetAdaptiveTableRecord();
                        node.children[ 1 ].resetAdaptiveTableRecord();

                        // Try to extend via projections
                        try {
                            this.template.extendForSetOperation( left, right );
                        } catch ( InvalidBinaryNodeException e ) {
                            this.traceAppendix = "\n\t\t"
                                    + "Failed on extension: "
                                    + node.type
                                    + ", Left Record: "
                                    + left.getColumns().toString()
                                    + ", Right Record: "
                                    + right.getColumns().toString()
                                    + "\n";
                            throw e;
                        }

                    }

                    node.all = this.template.nextAll();
                    node.setAdaptiveTableRecord( AdaptiveTableRecord.from( left ) );

                    try {
                        node.children[ 0 ].fields = left.getColumns().toArray( new String[0] );
                        node.children[ 1 ].fields = right.getColumns().toArray( new String[0] );
//                        if ( log.isDebugEnabled() ) {
//                            log.debug( "Projections... left: {},  right: {}", node.children[ 0 ].getAdaptiveTableRecord().getColumns(), node.children[ 1 ].getAdaptiveTableRecord().getColumns() );
//                            log.debug( "Projections... left: {},  right: {}", left.getColumns(), right.getColumns() );
//                        }
                        this.retroactiveProjections( algBuilder, node );
                    } catch ( IllegalArgumentException e ) {
                        throw new AdaptiveOptTreeGenException( "Could not retroactively insert projections", e );
                    }

                    this.traceStack.push( null );
                    this.traceStack.push( node.children[ 0 ] );
                    this.traceStack.push( node.children[ 1 ] );

                }

                break;
            // Join Operations
            case "Join":
                // Check if Join Operation is possible
                Pair<String, String> pair = this.template.nextJoinColumns( left, right );

                if ( pair != null ) {
                    // Join Operation possible
                    node.join = this.template.nextJoinType();
                    node.setAdaptiveTableRecord( AdaptiveTableRecord.join( left, right, pair.left, pair.right, node.join ) );
                    node.col1 = pair.left;
                    node.col2 = pair.right;
                    node.operator = this.template.nextJoinOperation();

                } else if ( this.template.switchToSetOpIfJoinNotPossible() ) {
                    // Switch to set operator
                    node.type = this.template.nextBinaryOperatorType();
                    recurse = true;
                } else {
                    // We need to alter the tree such that the Join Operation is Valid
//                    if ( log.isDebugEnabled() ) {
//                        log.debug( "Generating projections to account for invalid join operation by extending tables for join operation..." );
//                        log.debug( "Old columns...  left: {},  right: {}", left.getColumns(), right.getColumns()  );
//                    }

                    // Insert Projections copying records from children left and right
                    AdaptiveNode.insertProjections( node );

                    // Get new child-nodes
                    left = node.children[ 0 ].getAdaptiveTableRecord();
                    right = node.children[ 1 ].getAdaptiveTableRecord();

//                    if ( log.isDebugEnabled() ) {
//                        log.debug( "Projections added to tree... left: {},  right: {}", left.getColumns(), right.getColumns() );
//                    }

                    // Try extending the table
                    try {
                        this.template.extendForJoinOperation( left, right );
                    } catch ( InvalidBinaryNodeException e ) {
                        this.traceAppendix = "\n\t\t"
                                + "Failed on extension: "
                                + node.type
                                + ", Left Record: "
                                + left.getColumns().toString()
                                + ", Right Record: "
                                + right.getColumns().toString()
                                + "\n";
                        throw e;
                    }


//                    if ( log.isDebugEnabled() ) {
//                        log.debug( "Projections changed accordingly... left: {},  right: {}", left.getColumns(), right.getColumns() );
//                    }


                    pair = this.template.nextJoinColumns( left, right );

                    if ( pair != null ) {
                        // Join Operation possible
                        node.join = this.template.nextJoinType();
                        node.setAdaptiveTableRecord( AdaptiveTableRecord.join( left, right, pair.left, pair.right, node.join ) );
                        node.col1 = pair.left;
                        node.col2 = pair.right;
                        node.operator = this.template.nextJoinOperation();
                    } else {
                        throw new InvalidBinaryNodeException( "Could not create a parsable Join Expression", null );
                    }

                    // Try to convert AlgNodes
                    try {
                        node.children[ 0 ].fields = left.getColumns().toArray( new String[0] );
                        node.children[ 1 ].fields = right.getColumns().toArray( new String[0] );
                        this.retroactiveProjections( algBuilder, node );
                    } catch ( IllegalArgumentException e ) {
                        throw new AdaptiveOptTreeGenException( "Could not retroactively insert projections", e );
                    }

                    this.traceStack.push( null );
                    this.traceStack.push( node.children[ 0 ] );
                    this.traceStack.push( node.children[ 1 ] );

                }

                break;
            default:
                throw new AdaptiveOptTreeGenException(
                        "failed to set mode for binary node",
                        new IllegalArgumentException( "Binary AdaptiveNode of type '" + node.type + "' is not supported yet." )
                );
        }

        // If an operator was switched due to incompatibility with the generated Tree
        if ( recurse ) {
            setModeForBinaryOperatorNode( algBuilder, node );
        }

    }


    /**
     * Configures a random unary operator node and handles edge cases.
     */
    private void setModeForUnaryOperatorNode(final AdaptiveNode node) throws AdaptiveOptTreeGenException {

        node.type = this.template.nextUnaryOperatorType();

        AdaptiveTableRecord adaptiveTableRecord = AdaptiveTableRecord.from( node.children[ 0 ].getAdaptiveTableRecord() );

        Pair<String, PolyType> pair;

        boolean recurse = false;

        switch ( node.type ) {
            case "Filter":

                if ( adaptiveTableRecord.getColumns().size() == 0) {
                    throw new AdaptiveOptTreeGenException( "No columns to filter", new IndexOutOfBoundsException() );
                }

                if ( adaptiveTableRecord.getColumns().size() == 1 ) {
                    pair = this.template.nextColumn( adaptiveTableRecord.getColumns().get( 0 ) );
                } else {
                    pair = this.template.nextColumn( adaptiveTableRecord );
                }

                node.setAdaptiveTableRecord( adaptiveTableRecord );
                node.field = pair.left;
                node.operator = this.template.nextFilterOperation();
                switch ( pair.right ) {
                    case BOOLEAN:
                        node.filter = "true";
                        break;
                    case TINYINT:
                    case SMALLINT:
                    case INTEGER:
                    case BIGINT:
                    case DECIMAL:
                    case FLOAT:
                    case REAL:
                    case DOUBLE:
                        node.filter = "0";
                        break;
                    case DATE:
                        node.filter = "date '2000-01-01'";
                        break;
                    case TIME:
                        node.filter = "time '00:00:00'";
                        break;
                    case TIME_WITH_LOCAL_TIME_ZONE:
                        node.filter = "time '00:00:00 GMT'";
                        break;
                    case TIMESTAMP:
                        node.filter = "timestamp '2000-01-01 00:00:00'";
                        break;
                    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                        node.filter = "timestamp '2000-01-01 00:00:00 GMT'";
                        break;
                    case CHAR:
                        node.filter = "$";
                        break;
                    case VARCHAR:
                        node.filter = "$varchar";
                        break;
                }
                break;
            case "Project":
                List<String> columns = adaptiveTableRecord.getColumns();

                if ( columns.size() > 2 ) {
                    int i = this.template.nextInt( columns.size() );
                    if ( i == 0 ) {
                        i++;
                    }
                    int j = this.template.nextInt( i );
                    columns = columns.subList( j, i );
                }

                node.fields = columns.toArray( String[]::new );
                node.setAdaptiveTableRecord( AdaptiveTableRecord.project( adaptiveTableRecord.getTableName(), columns ) );
                break;
            case "Sort":
                if ( adaptiveTableRecord.getColumns().size() == 0) {
                    throw new AdaptiveOptTreeGenException( "No columns to sort", new IndexOutOfBoundsException() );
                }

                if ( adaptiveTableRecord.getColumns().size() == 1 ) {
                    pair = this.template.nextColumn( adaptiveTableRecord.getColumns().get( 0 ) );
                } else {
                    pair = this.template.nextColumn( adaptiveTableRecord );
                }

                SortState sortState = new SortState();
                sortState.sorting = true;
                sortState.column = pair.left;
                node.sortColumns = new SortState[] { sortState };
                node.setAdaptiveTableRecord( adaptiveTableRecord );
                break;
            default:
                throw new AdaptiveOptTreeGenException(
                        "failed to set mode for unary node",
                        new IllegalArgumentException( "Binary AdaptiveNode of type '" + node.type + "' is not supported yet." )
                );
        }

        // If an operator was switched due to incompatibility with the generated Tree
        if ( recurse ) {
            setModeForUnaryOperatorNode( node );
        }

    }


    /**
     * Configures TableScan node.
     */
    private void setModeForTableScan(final AdaptiveNode node) {
        Pair<String, TableRecord> pair = this.template.nextTable();
        node.type = "TableScan";
        node.setAdaptiveTableRecord( AdaptiveTableRecord.from( ( AdaptiveTableRecord ) pair.right ) );
        node.tableName = pair.left + "." + pair.right.getTableName();
    }


    /**
     * Converts an AdaptiveNode to an AlgNode.
     */
    private AlgBuilder convertNode( AlgBuilder algBuilder, final AdaptiveNode node ) throws AdaptiveOptTreeGenException {

        try {
            switch ( node.type ) {
                case "TableScan":
                    return this.tableScan( algBuilder, node );
                case "Join":
                    return this.join( algBuilder, node );
                case "Filter":
                    return this.filter( algBuilder, node );
                case "Project":
                    return this.project( algBuilder, node );
                case "Aggregate":
                    return this.aggregate( algBuilder, node );
                case "Sort":
                    return this.sort( algBuilder, node );
                case "Union":
                case "Minus":
                case "Intersect":
                    return this.setOp( algBuilder, node );
                default:
                    throw new IllegalArgumentException( "AdaptiveNode of type '" + node.type + "' is not supported yet." );
            }
        } catch ( Exception e ) {
            throw new AdaptiveOptTreeGenException( "Could not convert AdaptiveNode", e );
        }

    }

    private AlgBuilder join( AlgBuilder algBuilder, AdaptiveNode node ) {
        String[] field1 = null;
        String[] field2 = null;
        if ( node.col1 != null ) {
            field1 = node.col1.split( "\\." );
        }
        if ( node.col2 != null ) {
            field2 = node.col2.split( "\\." );
        }
        return algBuilder.join( node.join, algBuilder.call( getOperator( node.operator ), algBuilder.field( node.inputCount, field1[0], field1[1] ), algBuilder.field( node.inputCount, field2[0], field2[1] ) ) );
    }

    private AlgBuilder tableScan( AlgBuilder algBuilder, AdaptiveNode node ) {
        return algBuilder.scan( Util.tokenize( node.tableName, "." ) ).as( node.tableName );
    }

    private AlgBuilder setOp( AlgBuilder algBuilder, AdaptiveNode node ) {
        String newAlias = this.template.nextUniqueString( this.treeCounter );
        node.setAdaptiveTableRecord( AdaptiveTableRecord.from( node.getAdaptiveTableRecord(), newAlias ) );

        switch ( node.type ) {
            case "Union":
                algBuilder.union( node.all, node.inputCount );
                break;
            case "Minus":
                algBuilder.minus( node.all, node.inputCount );
                break;
            case "Intersect":
                algBuilder.intersect( node.all, node.inputCount );
                break;
        }

        return algBuilder.as( newAlias ).as( node.children[ 0 ].getAdaptiveTableRecord().getTableName() );

    }

    private AlgBuilder sort( AlgBuilder algBuilder, AdaptiveNode node ) {
        ArrayList<RexNode> columns = new ArrayList<>();
        for ( SortState s : node.sortColumns ) {
            String[] sortField = s.column.split( "\\." );
            String[] aliases1 = sortField[0].split( "___" );
            String alias1 = ( aliases1.length == 0 ) ? sortField[ 0 ] : aliases1[ 0 ];
            if ( s.direction == SortDirection.DESC ) {
                columns.add( algBuilder.desc( algBuilder.field( node.inputCount, alias1, sortField[1] ) ) );
            } else {
                columns.add( algBuilder.field( node.inputCount, alias1, sortField[1] ) );
            }
        }
        return algBuilder.sort( columns );
    }

    private AlgBuilder aggregate( AlgBuilder algBuilder, AdaptiveNode node ) {
        AlgBuilder.AggCall aggregation;
        String[] aggFields = node.field.split( "\\." );
        String[] aliases0 = aggFields[0].split( "___" );
        String alias0 = ( aliases0.length == 0 ) ? aggFields[ 0 ] : aliases0[ 0 ];
        switch ( node.aggregation ) {
            case "SUM":
                aggregation = algBuilder.sum( false, node.alias, algBuilder.field( node.inputCount, alias0, aggFields[1] ) );
                break;
            case "COUNT":
                aggregation = algBuilder.count( false, node.alias, algBuilder.field( node.inputCount, alias0, aggFields[1] ) );
                break;
            case "AVG":
                aggregation = algBuilder.avg( false, node.alias, algBuilder.field( node.inputCount, alias0, aggFields[1] ) );
                break;
            case "MAX":
                aggregation = algBuilder.max( node.alias, algBuilder.field( node.inputCount, alias0, aggFields[1] ) );
                break;
            case "MIN":
                aggregation = algBuilder.min( node.alias, algBuilder.field( node.inputCount, alias0, aggFields[1] ) );
                break;
            default:
                throw new IllegalArgumentException( "unknown aggregate type" );
        }
        if ( node.groupBy == null || node.groupBy.equals( "" ) ) {
            return algBuilder.aggregate( algBuilder.groupKey(), aggregation );
        } else {
            return algBuilder.aggregate( algBuilder.groupKey( node.groupBy ), aggregation );
        }
    }

    private AlgBuilder filter( AlgBuilder algBuilder, AdaptiveNode node ) {
        String[] field = node.field.split( "\\." );
        String[] aliases = field[0].split( "___" );
        String alias = ( aliases.length == 0 ) ? field[ 0 ] : aliases[ 0 ];
        if ( NumberUtils.isNumber( node.filter ) ) {
            Number filter;
            double dbl = Double.parseDouble( node.filter );
            filter = dbl;
            if ( dbl % 1 == 0 ) {
                filter = Integer.parseInt( node.filter );
            }
            return algBuilder.filter( algBuilder.call( getOperator( node.operator ), algBuilder.field( node.inputCount, alias, field[1] ), algBuilder.literal( filter ) ) );
        } else {
            return algBuilder.filter( algBuilder.call( getOperator( node.operator ), algBuilder.field( node.inputCount, alias, field[1] ), algBuilder.literal( node.filter ) ) );
        }
    }

    private AlgBuilder project( AlgBuilder algBuilder, AdaptiveNode node ) throws IllegalArgumentException {
        ArrayList<RexNode> fields = getFields( node.fields, node.inputCount, algBuilder );
        return algBuilder.project( fields );
    }

    private void retroactiveProjections( AlgBuilder algBuilder, AdaptiveNode node ) throws IllegalArgumentException {
        AdaptiveNode left = node.children[ 0 ];
        AdaptiveNode right = node.children[ 1 ];

        this.project( algBuilder, right );

        AlgNode algNode = algBuilder.build();

        this.project( algBuilder, left );

        algBuilder.push( algNode );

    }

    /**
     * Parse an operator and return it as SqlOperator. Copied From QueryPlanBuilder.java
     *
     * @param operator operator for a filter condition
     * @return parsed operator as SqlOperator
     */
    private static Operator getOperator( final String operator ) {
        switch ( operator ) {
            case "=":
                return OperatorRegistry.get( OperatorName.EQUALS );
            case "!=":
            case "<>":
                return OperatorRegistry.get( OperatorName.NOT_EQUALS );
            case "<":
                return OperatorRegistry.get( OperatorName.LESS_THAN );
            case "<=":
                return OperatorRegistry.get( OperatorName.LESS_THAN_OR_EQUAL );
            case ">":
                return OperatorRegistry.get( OperatorName.GREATER_THAN );
            case ">=":
                return OperatorRegistry.get( OperatorName.GREATER_THAN_OR_EQUAL );
            default:
                throw new IllegalArgumentException( "Operator '" + operator + "' is not supported." );
        }
    }


    /**
     *  Copied From QueryPlanBuilder.java
     */
    private static ArrayList<RexNode> getFields( String[] fields, int inputCount, AlgBuilder builder ) throws IllegalArgumentException {
        ArrayList<RexNode> nodes = new ArrayList<>();
        for ( String f : fields ) {
            if ( f.equals( "" ) ) {
                continue;
            }
            String[] field = f.split( "\\." );
            // Todo ask about this issue here....
            nodes.add( builder.field( inputCount, field[0], field[1] ) );
        }
        return nodes;
    }


    /**
     * Dumps the generated Tree to a string representation for debug purposes.
     */
    private String dumpTreeGenerationTrace() {
        StringJoiner stringJoiner = new StringJoiner( "\n", "\n", "" );

        stringJoiner.add( "Total Nodes Generated: " + this.nodeCounter );

        if ( this.traceAppendix != null ) {
            stringJoiner.add( this.traceAppendix );
        }

        while ( ! this.traceStack.isEmpty() ) {
            AdaptiveNode node = this.traceStack.pop();

            if ( node == null ) {
                stringJoiner.add( "\t\t Retroactive Projections -> " );
                continue;
            }

            StringJoiner infoJoiner = new StringJoiner( ", ", "(", ")" );
            switch ( node.type ) {
                case "Join":
                    infoJoiner
                            .add( node.join.name() )
                            .add( node.col1 )
                            .add( node.col2 );
                    break;
                case "Union":
                    break;
                case "Intersect":
                    break;
                case "Minus":
                    break;
                case "Filter":
                    infoJoiner
                            .add( node.field )
                            .add( node.operator )
                            .add( node.filter );
                    break;
                case "Project":
                    infoJoiner
                            .add( Arrays.toString( node.fields ) );
                    break;
                case "Sort":
                    infoJoiner
                            .add( node.sortColumns[0].column )
                            .add( node.sortColumns[0].direction.name() );
                    break;
                case "TableScan":
                    infoJoiner.add( node.tableName );
                    break;
            }

            stringJoiner.add(
                    new StringBuilder( "\t".repeat( node.getDepth() ) )
                            .append( " | " )
                            .append( node.type )
                            .append( infoJoiner )
            );

        }

        return stringJoiner.toString();

    }

    @Override
    public String getStringRep() {
        return this.dumpTreeGenerationTrace();
    }

}

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

package org.polypheny.db.schema.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonMaxKey;
import org.bson.BsonMinKey;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.BsonValue;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.relational.RelScan;
import org.polypheny.db.algebra.logical.document.LogicalDocumentAggregate;
import org.polypheny.db.algebra.operators.OperatorName;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.DocumentType;
import org.polypheny.db.languages.OperatorRegistry;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.nodes.Operator;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.DateString;
import org.polypheny.db.util.Pair;
import org.polypheny.db.util.TimestampString;

public class DocumentUtil {


    public static boolean validateJson( String json ) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.readTree( json );
            return true;
        } catch ( JsonProcessingException e ) {
            return false;
        }

    }


    public static Pair<Class<? extends BsonValue>, Class<?>> getBsonClass( int typeNumber ) {
        switch ( typeNumber ) {
            case 1:
                return new Pair<>( BsonDouble.class, Double.class );
            case 2:
                return new Pair<>( BsonString.class, String.class );
            case 3:
                return new Pair<>( BsonDocument.class, Object.class );
            case 4:
                return new Pair<>( BsonArray.class, List.class );
            case 5:
                return new Pair<>( BsonBinary.class, Byte.class );
            case 6: // undefined
                throw new RuntimeException( "DEPRECATED" );
            case 7:
                return new Pair<>( BsonObjectId.class, ObjectId.class );
            case 8:
                return new Pair<>( BsonBoolean.class, Boolean.class );
            case 9:
                return new Pair<>( BsonDateTime.class, DateString.class );
            case 10:
                return new Pair<>( BsonNull.class, null );
            case 11:
                return new Pair<>( BsonRegularExpression.class, String.class );
            case 12: // dbPointer
                throw new RuntimeException( "DEPRECATED" );
            case 13:
                //return new Pair<>( BsonJavaScript.class, String.class );
                throw new RuntimeException( "UNSUPPORTED" );
            case 14: // Symbol
                throw new RuntimeException( "DEPRECATED" );
            case 15:
                //return new Pair<>( BsonJavaScriptWithScope.class, String.class );
                throw new RuntimeException( "UNSUPPORTED" );
            case 16:
                return new Pair<>( BsonInt32.class, Integer.class );
            case 17:
                return new Pair<>( BsonTimestamp.class, TimestampString.class );
            case 18:
                return new Pair<>( BsonInt64.class, Long.class );
            case 19:
                return new Pair<>( BsonDecimal128.class, BigDecimal.class );
            case -1:
                return new Pair<>( BsonMinKey.class, BsonMinKey.class );
            case 127:
                return new Pair<>( BsonMaxKey.class, BsonMaxKey.class );
            default:
                throw new RuntimeException( "This type does not exist." );
        }
    }


    public static int getTypeNumber( String value ) {
        switch ( value ) {
            case "double":
                return 1;
            case "string":
                return 2;
            case "object":
                return 3;
            case "array":
                return 4;
            case "binData":
                return 5;
            case "undefined":
                throw new RuntimeException( "DEPRECATED" );
            case "objectId":
                return 7;
            case "bool":
                return 8;
            case "date":
                return 9;
            case "null":
                return 10;
            case "regex":
                return 11;
            case "dbPointer":
                throw new RuntimeException( "DEPRECATED" );
            case "javascript":
                throw new RuntimeException( "UNSUPPORTED" );
            case "symbol":
                throw new RuntimeException( "DEPRECATED" );
            case "javascriptWithScope":
                throw new RuntimeException( "DEPRECATED" );
            case "int":
                return 16;
            case "timestamp":
                return 17;
            case "long":
                return 18;
            case "decimal":
                return 19;
            case "minKey":
                return -1;
            case "maxKey":
                return 127;
            case "number":
                return 99; // not official mongodb
            default:
                throw new RuntimeException( "This type does not exist." );
        }
    }


    public static BsonValue getBson( Object object ) {
        if ( object instanceof String ) {
            return new BsonString( (String) object );
        } else if ( object instanceof Integer ) {
            return new BsonInt32( (Integer) object );
        } else if ( object instanceof Long ) {
            return new BsonInt64( (Long) object );
        } else if ( object instanceof BigDecimal ) {
            return new BsonDecimal128( new Decimal128( ((BigDecimal) object) ) );
        } else if ( object instanceof Double ) {
            return new BsonDouble( (Double) object );
        } else if ( object instanceof List ) {
            return new BsonArray( ((List<?>) object).stream().map( DocumentUtil::getBson ).collect( Collectors.toList() ) );
        } else {
            throw new RuntimeException( "Type not considered" );
        }
    }


    public static List<Integer> removePlaceholderTypes( List<Integer> types ) {
        List<Integer> typeNumbers = new ArrayList<>( types );
        if ( typeNumbers.contains( 99 ) ) {
            // number type matches to double, 32int, 64int, decimal
            typeNumbers.remove( (Integer) 99 );
            typeNumbers.add( 1 );
            typeNumbers.add( 16 );
            typeNumbers.add( 18 );
            typeNumbers.add( 19 );
        }
        return typeNumbers;
    }


    /**
     * Updates contain RENAME, REMOVE, REPLACE parts and are merged into a single DOC_UPDATE in this method
     *
     * @param rowType the default rowtype at this point
     * @param node the transformed operation up to this step e.g. {@link RelScan} or {@link LogicalDocumentAggregate}
     * @return the unified UPDATE AlgNode
     */
    public static Pair<List<String>, List<RexNode>> transformUpdateRelational(
            Map<String, RexNode> updates,
            List<String> removes,
            Map<String, String> renames,
            AlgDataType rowType,
            AlgNode node ) {
        AlgOptCluster cluster = node.getCluster();
        RexNode updateChain = cluster.getRexBuilder().makeInputRef( rowType, 0 );

        // replace
        if ( !updates.isEmpty() ) {
            updateChain = new RexCall(
                    new DocumentType(),
                    OperatorRegistry.get( QueryLanguage.from( "mongo" ), OperatorName.MQL_UPDATE_REPLACE ),
                    Arrays.asList(
                            updateChain,
                            getStringArray( List.copyOf( updates.keySet() ), cluster ),
                            getArray( List.copyOf( updates.values() ), new DocumentType() ) ) );
        }

        // rename
        if ( !renames.isEmpty() ) {
            updateChain = new RexCall(
                    new DocumentType(),
                    OperatorRegistry.get( QueryLanguage.from( "mongo" ), OperatorName.MQL_UPDATE_RENAME ),
                    Arrays.asList(
                            updateChain,
                            getStringArray( List.copyOf( renames.keySet() ), cluster ),
                            getStringArray( List.copyOf( renames.values() ), cluster ) ) );
        }

        // remove
        if ( !removes.isEmpty() ) {
            updateChain = new RexCall(
                    new DocumentType(),
                    OperatorRegistry.get( QueryLanguage.from( "mongo" ), OperatorName.MQL_UPDATE_REMOVE ),
                    Arrays.asList(
                            updateChain,
                            getStringArray( removes, cluster ) ) );
        }

        if ( !removes.isEmpty() ) {
            updateChain = new RexCall(
                    new DocumentType(),
                    OperatorRegistry.get(
                            QueryLanguage.from( "mongo" ),
                            OperatorName.MQL_UPDATE ),
                    Arrays.asList(
                            updateChain,
                            getStringArray( removes, cluster ) ) );
        }

        return Pair.of(
                List.of( DocumentType.DOCUMENT_DATA ),
                List.of( updateChain ) );
    }


    public static RexCall createJsonify( RexNode ref, AlgDataType type ) {
        return new RexCall( type, OperatorRegistry.get( QueryLanguage.from( "mongo" ), OperatorName.MQL_JSONIFY ), Collections.singletonList( ref ) );
    }


    public static RexCall getStringArray( List<String> elements, AlgOptCluster cluster ) {
        List<RexNode> rexNodes = new ArrayList<>();
        int maxSize = 0;
        for ( String name : elements ) {
            rexNodes.add( cluster.getRexBuilder().makeLiteral( name ) );
            maxSize = Math.max( name.length(), maxSize );
        }

        AlgDataType type = cluster.getTypeFactory().createArrayType(
                cluster.getTypeFactory().createPolyType( PolyType.CHAR, maxSize ),
                rexNodes.size() );
        return getArray( rexNodes, type );
    }


    public static RexCall getArray( List<RexNode> elements, AlgDataType type ) {
        return new RexCall( type, OperatorRegistry.get( OperatorName.ARRAY_VALUE_CONSTRUCTOR ), elements );
    }


    /**
     * Defines one of the possible doc update operations
     */
    public enum UpdateOperation {
        RENAME( OperatorRegistry.get( QueryLanguage.from( "mongo" ), OperatorName.MQL_UPDATE_RENAME ) ),
        REPLACE( OperatorRegistry.get( QueryLanguage.from( "mongo" ), OperatorName.MQL_UPDATE_REPLACE ) ),
        REMOVE( OperatorRegistry.get( QueryLanguage.from( "mongo" ), OperatorName.MQL_UPDATE_REMOVE ) );

        @Getter
        private final Operator operator;


        UpdateOperation( Operator operator ) {
            this.operator = operator;
        }
    }

}

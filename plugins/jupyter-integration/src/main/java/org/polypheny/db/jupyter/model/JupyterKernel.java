/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.db.jupyter.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.URI;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.api.Session;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.jupyter.JupyterPlugin;
import org.polypheny.db.jupyter.model.language.JupyterKernelLanguage;
import org.polypheny.db.jupyter.model.language.JupyterLanguageFactory;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.webui.crud.LanguageCrud;
import org.polypheny.db.webui.models.Result;
import org.polypheny.db.webui.models.requests.QueryRequest;

@Slf4j
public class JupyterKernel {

    @Getter
    private final String name, kernelId, clientId;
    private final JupyterKernelLanguage kernelLanguage;
    @Getter
    private final boolean supportsPolyCells;
    private final WebSocket webSocket;
    private final Set<Session> subscribers = new HashSet<>();
    private final Gson gson = new Gson();
    private final Gson resultSetGson;
    private final JsonObject statusMsg;
    private boolean isRestarting = false;
    private final Map<String, ActivePolyCell> activePolyCells = new ConcurrentHashMap<>();


    public JupyterKernel( String kernelId, String name, WebSocket.Builder builder, String host ) {
        this.kernelId = kernelId;
        this.name = name;
        this.clientId = UUID.randomUUID().toString();
        this.kernelLanguage = JupyterLanguageFactory.getKernelLanguage( name );

        this.supportsPolyCells = this.kernelLanguage != null;

        String url = "ws://" + host + "/api/kernels/" + this.kernelId + "/channels?session_id=" + clientId;

        this.webSocket = builder.buildAsync( URI.create( url ), new WebSocketClient() ).join();

        this.statusMsg = new JsonObject();
        this.statusMsg.addProperty( "msg_type", "status" );
        JsonObject content = new JsonObject();
        content.addProperty( "execution_state", "starting" );
        this.statusMsg.add( "content", content );
        sendInitCode();

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter( Result.class, Result.getSerializer() );
        resultSetGson = gsonBuilder.create();
    }


    public void subscribe( Session session ) {
        subscribers.add( session );
    }


    public void unsubscribe( Session session ) {
        subscribers.remove( session );
    }


    private void handleText( CharSequence data ) {
        log.info( "Received Text: {}", data );
        String dataStr = data.toString();
        if ( dataStr.length() < 10000 ) { // reduce number of large json strings that need to be parsed
            try {
                JsonObject json = gson.fromJson( dataStr, JsonObject.class );
                String msgType = json.get( "msg_type" ).getAsString();
                if ( msgType.equals( "status" ) ) {
                    statusMsg.add( "content", json.getAsJsonObject( "content" ) );
                } else if ( msgType.equals( "shutdown_reply" ) ) {
                    JsonObject content = json.getAsJsonObject( "content" );
                    String status = content.get( "status" ).getAsString();
                    boolean restart = content.get( "restart" ).getAsBoolean();
                    activePolyCells.clear();
                    if ( status.equals( "ok" ) && restart ) {
                        isRestarting = true;
                    }
                } else if ( !activePolyCells.isEmpty() && msgType.equals( "input_request" ) ) {
                    String query = json.getAsJsonObject( "content" ).get( "prompt" ).getAsString();
                    JsonObject parentHeader = json.getAsJsonObject( "parent_header" );
                    ActivePolyCell apc = activePolyCells.remove( parentHeader.get( "msg_id" ).getAsString() );
                    if ( apc == null ) {
                        return;
                    }
                    String result = anyQuery( query, apc.language, apc.namespace );
                    log.warn( "sending query result: {}", result );
                    ByteBuffer request = buildInputReply( result, parentHeader );
                    webSocket.sendBinary( request, true );
                    return;
                }
            } catch ( JsonSyntaxException ignored ) {

            }
        }

        subscribers.forEach( s -> {
            try {
                s.getRemote().sendString( dataStr );
            } catch ( IOException e ) {
                s.close();
            }
        } );
    }


    public String getStatusMessage() {
        return statusMsg.toString();
    }


    public void execute( String code, String uuid ) {
        log.info( "Sending code: {}", code );
        ByteBuffer request = buildExecutionRequest( code, uuid, false, false, true );
        webSocket.sendBinary( request, true );
    }


    public void executePolyCell( String query, String uuid, String language, String namespace,
            String variable, boolean expandParams ) {
        if ( query.strip().length() == 0 ) {
            execute( "", uuid ); // properly terminates the request
            return;
        }
        if ( isRestarting ) {
            sendInitCode();
            isRestarting = false;
        }
        try {
            String[] queries = kernelLanguage.transformToQuery( query, language, namespace, variable, expandParams );
            activePolyCells.put( uuid, new ActivePolyCell( language, namespace ) );

            for ( String code : queries ) {
                // TODO: correctly silence cells
                ByteBuffer request = buildExecutionRequest( code, uuid, false, true, true );
                webSocket.sendBinary( request, true );
                log.error( "sending query code: {}", code );

            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

    }


    private String anyQuery( String query, String language, String namespace ) {
        QueryRequest queryRequest = new QueryRequest( query, false, true, language, namespace );
        List<Result> results = LanguageCrud.anyQuery(
                QueryLanguage.from( language ),
                null,
                queryRequest,
                JupyterPlugin.transactionManager,
                Catalog.defaultUserId,
                Catalog.defaultDatabaseId,
                null );
        return resultSetGson.toJson( results );
    }


    private void sendInitCode() {
        if ( supportsPolyCells ) {
            String initCode = kernelLanguage.getInitCode();
            if ( initCode != null ) {
                ByteBuffer request = buildExecutionRequest( initCode, true, false, true );
                webSocket.sendBinary( request, true );
            }
        }
    }


    private ByteBuffer buildExecutionRequest( String code, String uuid, boolean silent, boolean allowStdin, boolean stopOnError ) {
        JsonObject header = new JsonObject();
        header.addProperty( "msg_id", uuid );
        header.addProperty( "msg_type", "execute_request" );
        header.addProperty( "version", "5.4" );

        JsonObject content = new JsonObject();
        content.addProperty( "silent", silent );
        content.addProperty( "allow_stdin", allowStdin );
        content.addProperty( "stop_on_error", stopOnError );
        content.addProperty( "code", code );

        return buildMessage( "shell", header, new JsonObject(), new JsonObject(), content );
    }


    private ByteBuffer buildExecutionRequest( String code, boolean silent, boolean allowStdin, boolean stopOnError ) {
        return buildExecutionRequest( code, UUID.randomUUID().toString(), silent, allowStdin, stopOnError );
    }


    private ByteBuffer buildInputReply( String reply, String uuid, JsonObject parent_header ) {
        JsonObject header = new JsonObject();
        header.addProperty( "msg_id", uuid );
        header.addProperty( "msg_type", "input_reply" );
        header.addProperty( "version", "5.4" );

        JsonObject content = new JsonObject();
        content.addProperty( "value", reply );

        return buildMessage( "stdin", header, parent_header, new JsonObject(), content );
    }


    private ByteBuffer buildInputReply( String reply, JsonObject parent_header ) {
        return buildInputReply( reply, UUID.randomUUID().toString(), parent_header );
    }


    private ByteBuffer buildMessage( String channel, JsonObject header, JsonObject parentHeader, JsonObject metadata, JsonObject content ) {
        JsonObject msg = new JsonObject();
        msg.addProperty( "channel", channel );
        msg.add( "header", header );
        msg.add( "parent_header", parentHeader );
        msg.add( "metadata", metadata );
        msg.add( "content", content );
        byte[] byteMsg = msg.toString().getBytes( StandardCharsets.UTF_8 );

        ByteBuffer bb = ByteBuffer.allocate( (byteMsg.length + 8) );
        bb.putInt( 1 ); // nbufs: no. of msg parts (= no. of offsets)
        bb.putInt( 8 ); // offset_0: position of msg in bytes
        bb.put( byteMsg );
        bb.rewind();
        return bb;
    }


    public void close() {
        subscribers.forEach( c -> c.close( 1000, "Websocket to Kernel was closed" ) );
        webSocket.abort();
    }


    public String getStatus() {
        return "Input: " + !webSocket.isInputClosed() +
                ", Output: " + !webSocket.isOutputClosed() +
                ", Subscribers: " + subscribers.size();
    }


    private class WebSocketClient implements WebSocket.Listener {

        private final StringBuilder textBuilder = new StringBuilder();


        @Override
        public void onOpen( WebSocket webSocket ) {
            WebSocket.Listener.super.onOpen( webSocket );
        }


        @Override
        public CompletionStage<?> onText( WebSocket webSocket, CharSequence data, boolean last ) {
            textBuilder.append( data );
            if ( last ) {
                handleText( textBuilder.toString() );
                textBuilder.setLength( 0 );
            }
            return WebSocket.Listener.super.onText( webSocket, data, last );
        }


        @Override
        public CompletionStage<?> onClose( WebSocket webSocket, int statusCode, String reason ) {
            log.error( "closed websocket to {}", kernelId );
            JupyterSessionManager.getInstance().removeKernel( kernelId );
            return Listener.super.onClose( webSocket, statusCode, reason );
        }


        @Override
        public void onError( WebSocket webSocket, Throwable error ) {
            log.error( "error in websocket to {}:\n{}", kernelId, error.getMessage() );
            error.printStackTrace();
            JupyterSessionManager.getInstance().removeKernel( kernelId );
            Listener.super.onError( webSocket, error );
        }


    }


    private static class ActivePolyCell {

        @Getter
        private final String language, namespace;


        public ActivePolyCell( String language, String namespace ) {
            this.language = language;
            this.namespace = namespace;
        }

    }

}

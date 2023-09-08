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

package org.polypheny.db.webui;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.plugin.bundled.CorsPluginConfig;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.polypheny.db.config.Config;
import org.polypheny.db.config.Config.ConfigListener;
import org.polypheny.db.config.ConfigManager;
import org.polypheny.db.config.Feedback;


/**
 * RESTful server used by the Web UI to interact with the Config Manager.
 */
@Slf4j
public class ConfigServer implements ConfigListener {

    private static final Gson gson = new Gson();


    public ConfigServer( final int port ) {

        Javalin http = Javalin.create( config -> {
            config.plugins.enableCors( cors -> cors.add( CorsPluginConfig::anyHost ) );
            config.staticFiles.add( "webapp" );
            config.jsonMapper( new JavalinJackson().updateMapper( mapper -> {
                mapper.setSerializationInclusion( JsonInclude.Include.NON_NULL );
            } ) );
        } ).start( port );

        http.ws( "/configWebSocket", new ConfigWebsocket() );
        configRoutes( http );
    }


    /**
     * Many routes just for testing.
     * Route getPage: get a WebUiPage as JSON (with all its groups and configs).
     */
    private void configRoutes( final Javalin http ) {
        String type = "application/json";
        Gson gson = new Gson();
        ConfigManager cm = ConfigManager.getInstance();

        http.get( "/getPageList", ctx -> ctx.result( cm.getWebUiPageList() ) );

        // get Ui of certain page
        http.post( "/getPage", ctx -> {
            //input: req: {pageId: 123}
            try {
                ctx.result( cm.getPage( ctx.body() ) );
            } catch ( Exception e ) {
                //if input not number or page does not exist
                ctx.result( "" );
            }
        } );

        // Save changes from WebUi
        http.post( "/updateConfigs", ctx -> {
            log.trace( ctx.body() );
            Type clazzType = new TypeToken<Map<String, Object>>() {
            }.getType();
            Map<String, Object> changes = gson.fromJson( ctx.body(), clazzType );
            StringBuilder feedback = new StringBuilder();
            boolean allValid = true;
            for ( Map.Entry<String, Object> entry : changes.entrySet() ) {
                Config c = cm.getConfig( entry.getKey() );
                switch ( c.getConfigType() ) {
                    case "ConfigInteger":
                        Double d = (Double) entry.getValue();
                        if ( !c.setInt( d.intValue() ) ) {
                            allValid = false;
                            appendError( feedback, entry, c );
                        }
                        break;
                    case "ConfigDouble":
                        if ( !c.setDouble( (double) entry.getValue() ) ) {
                            allValid = false;
                            appendError( feedback, entry, c );
                        }
                        break;
                    case "ConfigDecimal":
                        if ( !c.setDecimal( (BigDecimal) entry.getValue() ) ) {
                            allValid = false;
                            appendError( feedback, entry, c );
                        }
                        break;
                    case "ConfigLong":
                        if ( !c.setLong( (long) entry.getValue() ) ) {
                            allValid = false;
                            appendError( feedback, entry, c );
                        }
                    case "ConfigString":
                        if ( !c.setString( (String) entry.getValue() ) ) {
                            allValid = false;
                            appendError( feedback, entry, c );
                        }
                        break;
                    case "ConfigBoolean":
                        if ( !c.setBoolean( (boolean) entry.getValue() ) ) {
                            allValid = false;
                            appendError( feedback, entry, c );
                        }
                        break;
                    case "ConfigClazz":
                    case "ConfigEnum":
                        if ( !c.parseStringAndSetValue( (String) entry.getValue() ) ) {
                            allValid = false;
                            appendError( feedback, entry, c );
                        }
                        break;
                    case "ConfigClazzList":
                    case "ConfigEnumList":
                        if ( !c.parseStringAndSetValue( gson.toJson( entry.getValue(), ArrayList.class ) ) ) {
                            allValid = false;
                            appendError( feedback, entry, c );
                        }
                        break;
                    case "ConfigList":
                        Feedback res = c.setConfigObjectList( (List<Object>) entry.getValue(), c.getTemplateClass() );
                        if ( !res.successful ) {
                            allValid = false;
                            if ( res.message.trim().equals( "" ) ) {
                                appendError( feedback, entry, c );
                            } else {
                                feedback.append( "Could not set " )
                                        .append( c.getKey() )
                                        .append( " due to: " )
                                        .append( res.message )
                                        .append( " " );
                            }

                        }
                        break;
                    default:
                        allValid = false;
                        feedback.append( "Config with type " ).append( c.getConfigType() ).append( " is not supported yet." );
                        log.error( "Config with type {} is not supported yet.", c.getConfigType() );
                }
            }
            if ( allValid ) {
                ctx.result( "{\"success\":1}" );
            } else {
                feedback.append( "All other values were saved." );
                ctx.result( "{\"warning\": \"" + feedback + "\"}" );
            }
        } );
    }


    private static void appendError( StringBuilder feedback, Entry<String, Object> entry, Config c ) {
        feedback.append( "Could not set " )
                .append( c.getKey() )
                .append( " to " )
                .append( entry.getValue() )
                .append( " because it was blocked by Java validation.\n" );
    }


    @Override
    public void onConfigChange( final Config c ) {
        Gson gson = new Gson();
        try {
            ConfigWebsocket.broadcast( gson.toJson( c ) );
        } catch ( IOException e ) {
            log.error( "Caught exception!", e );
        }
    }


    @Override
    public void restart( final Config c ) {

    }

}

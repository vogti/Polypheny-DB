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

package org.polypheny.db.config;


import com.google.gson.Gson;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.config.Config.ConfigListener;
import org.polypheny.db.config.exception.ConfigRuntimeException;


/**
 * ConfigManager allows to add and retrieve configuration objects.
 * If the configuration element has a Web UI Group and Web UI Page defined, it can be requested from the Web UI and the value of the configuration can be changed there.
 */
@Slf4j
public class ConfigManager {

    private static ConfigManager instance = new ConfigManager();

    private static String configurationFile = "polypheny.conf";
    private static String configurationDirectory = "config"; // Configuration directory shall always be placed next to executable

    private final ConcurrentMap<String, Config> configs;
    private final ConcurrentMap<String, WebUiGroup> uiGroups;
    private final ConcurrentMap<String, WebUiPage> uiPages;


    private static com.typesafe.config.Config configFile;

    private static File applicationConfFile = null;


    private ConfigManager() {
        this.configs = new ConcurrentHashMap<>();
        this.uiGroups = new ConcurrentHashMap<>();
        this.uiPages = new ConcurrentHashMap<>();
    }


    /**
     * Singleton
     */
    public static ConfigManager getInstance() {

        if ( configFile == null ) {
            loadConfigFile();
        }
        return instance;
    }


    private static void createConfigFolders( String workingDir, File configDir ) {
        if ( !new File( workingDir ).exists() ) {
            if ( !new File( workingDir ).mkdirs() ) {
                throw new RuntimeException( "Could not create the folders for " + new File( workingDir ).getAbsolutePath() );
            }
        }
        if ( !configDir.exists() ) {
            if ( !configDir.mkdirs() ) {
                throw new RuntimeException( "Could not create the config folder: " + configDir.getAbsolutePath() );
            }
        }
    }


    public static void loadConfigFile() {

        // No custom location has been specified
        // Assume Default
        if ( applicationConfFile == null ) {
            // Determine workingDirectory elsewhere. Maybe during installation or absolute path?!
            String workingDir = "./";
            File configDir = new File( new File( workingDir ), configurationDirectory );
            createConfigFolders( workingDir, configDir );
            applicationConfFile = new File( configDir, configurationFile );
        }

        if ( configFile == null ) {
            configFile = ConfigFactory.parseFile( applicationConfFile );
        } else {
            configFile = ConfigFactory.parseFile( applicationConfFile ).withFallback( configFile );
        }
    }


    private static void writeConfiguration( final com.typesafe.config.Config configuration ) {
        ConfigRenderOptions configRenderOptions = ConfigRenderOptions.defaults();
        configRenderOptions = configRenderOptions.setComments( false );
        configRenderOptions = configRenderOptions.setFormatted( true );
        configRenderOptions = configRenderOptions.setJson( false );
        configRenderOptions = configRenderOptions.setOriginComments( false );

        String workingDir = "./";
        File configDir = new File( new File( workingDir ), configurationDirectory );
        createConfigFolders( workingDir, configDir );
        try (
                FileOutputStream fos = new FileOutputStream( applicationConfFile, false );
                BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( fos ) )
        ) {
            bw.write( configuration.root().render( configRenderOptions ) );
        } catch ( IOException e ) {
            log.error( "Exception while writing configuration file", e );
        }
        loadConfigFile();
    }


    public static void setApplicationConfFile( File applicationConfFile ) {
        ConfigManager.applicationConfFile = applicationConfFile;

        configurationFile = applicationConfFile.getName();
        configurationDirectory = applicationConfFile.getAbsolutePath();
    }


    /**
     * Register a configuration element in the ConfigManager.
     * Either the default value is used. Or if the key is present within the configuration file, this
     * value will be used instead.
     *
     * @param config Configuration element to register.
     * @throws ConfigRuntimeException If a Config is already registered.
     */
    public void registerConfig( final Config config ) {
        if ( this.configs.containsKey( config.getKey() ) ) {
            throw new ConfigRuntimeException( "Cannot register two configuration elements with the same key: " + config.getKey() );
        } else {
            // Check if the config file contains this key and if so set the value to the one defined in the config file
            if ( configFile.hasPath( config.getKey() ) ) {
                config.setValueFromFile( configFile );
            }
            this.configs.put( config.getKey(), config );
        }
    }


    /**
     * Register multiple configuration elements in the ConfigManager.
     *
     * @param configs Configuration elements to register
     */
    public void registerConfigs( final Config... configs ) {
        for ( Config c : configs ) {
            this.registerConfig( c );
        }
    }


    public void observeAll( final ConfigListener listener ) {
        for ( Config c : configs.values() ) {
            c.addObserver( listener );
        }
    }


    /**
     * Updates Config to file
     */
    public void persistConfigValue( String configKey, Object updatedValue ) {

        // TODO Extend with deviations from default Value, the actual defaultValue, description and link to website

        com.typesafe.config.Config newConfig;

        // Check if the new value is default value.
        // If so, the value will be omitted since there is no need to write it to file
        if ( configs.get( configKey ).isDefault() ) {
            //if ( updatedValue.toString().equals( configs.get( configKey ).getDefaultValue().toString() ) ) {
            log.warn( "Updated value: '{}' for key: '{}' is equal to default value. Omitting.", updatedValue, configKey );
            newConfig = configFile.withoutPath( configKey );
        } else {
            newConfig = configFile.withValue( configKey, ConfigValueFactory.fromAnyRef( updatedValue ) );
        }
        writeConfiguration( newConfig );

    }


    /**
     * Get configuration as Configuration object
     */
    public Config getConfig( final String s ) {
        return configs.get( s );
    }


    /**
     * Register a Web UI Group in the ConfigManager.
     * A Web UI Group consists of several Configs that will be displayed together in the Web UI.
     *
     * @param group WebUiGroup to register
     * @throws ConfigRuntimeException If a group with that key already exists.
     */
    public void registerWebUiGroup( final WebUiGroup group ) {
        if ( this.uiGroups.containsKey( group.getId() ) ) {
            throw new ConfigRuntimeException( "Cannot register two WeUiGroups with the same key: " + group.getId() );
        } else {
            this.uiGroups.put( group.getId(), group );
        }
    }


    /**
     * Register a Web UI Page in the ConfigManager.
     * A Web UI Page consists of several Web UI Groups that will be displayed together in the Web UI.
     *
     * @param page WebUiPage to register
     * @throws ConfigRuntimeException If a page with that key already exists.
     */
    public void registerWebUiPage( final WebUiPage page ) {
        if ( this.uiPages.containsKey( page.getId() ) ) {
            throw new ConfigRuntimeException( "Cannot register two WebUiPages with the same key: " + page.getId() );
        } else {
            this.uiPages.put( page.getId(), page );
        }
    }


    /**
     * Generates a Json of all the Web UI Pages in the ConfigManager (for the sidebar in the Web UI)
     * The Json does not contain the groups and configs of the Web UI Pages
     */
    public String getWebUiPageList() {
        //todo recursion with parentPage field
        // Angular wants: { id, name, icon, children[] }
        ArrayList<PageListItem> out = new ArrayList<>();
        for ( WebUiPage p : uiPages.values() ) {
            out.add( new PageListItem( p.getId(), p.getTitle(), p.getIcon(), p.getLabel() ) );
        }
        out.sort( Comparator.comparing( PageListItem::getName ) );
        Gson gson = new Gson();
        return gson.toJson( out );
    }


    /**
     * Get certain page as json.
     * Groups within a page and configs within a group are sorted in the Web UI, not here.
     *
     * @param id The id of the page
     */
    public String getPage( final String id ) {
        // fill WebUiGroups with Configs
        for ( ConcurrentMap.Entry<String, Config> c : configs.entrySet() ) {
            try {
                String i = c.getValue().getWebUiGroup();
                this.uiGroups.get( i ).addConfig( c.getValue() );
            } catch ( NullPointerException e ) {
                // TODO: This is not nice...
                // Skipping config with no WebUiGroup
            }
        }

        // fill WebUiPages with WebUiGroups
        for ( ConcurrentMap.Entry<String, WebUiGroup> g : uiGroups.entrySet() ) {
            try {
                String i = g.getValue().getPageId();
                this.uiPages.get( i ).addWebUiGroup( g.getValue() );
            } catch ( NullPointerException e ) {
                // TODO: This is not nice...
                // Skipping config with no page id
            }
        }
        return uiPages.get( id ).toString();
    }


    /**
     * The class PageListItem will be converted into a Json String by Gson.
     * The Web UI requires a Json Object with the fields id, name, icon, children[] for the Sidebar.
     * This class is required to convert a WebUiPage object into the format needed by the Angular WebUi
     */
    static class PageListItem {

        @SuppressWarnings({ "FieldCanBeLocal", "unused" })
        private String id;
        @Getter
        private String name;
        @SuppressWarnings({ "FieldCanBeLocal", "unused" })
        private String icon;
        @SuppressWarnings({ "FieldCanBeLocal", "unused" })
        private String label;
        @SuppressWarnings({ "unused" })
        private PageListItem[] children;


        PageListItem( final String id, final String name, final String icon, final String label ) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.label = label;
        }


        @Override
        public String toString() {
            Gson gson = new Gson();
            return gson.toJson( this );
        }
    }

}

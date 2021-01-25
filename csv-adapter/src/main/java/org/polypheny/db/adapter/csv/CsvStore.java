package org.polypheny.db.adapter.csv;


import com.google.common.collect.ImmutableList;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.adapter.csv.CsvTable.Flavor;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.CatalogIndex;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.jdbc.Context;
import org.polypheny.db.schema.Schema;
import org.polypheny.db.schema.SchemaPlus;
import org.polypheny.db.schema.Table;
import org.polypheny.db.transaction.PolyXid;


@Slf4j
public class CsvStore extends DataStore {

    @SuppressWarnings("WeakerAccess")
    public static final String ADAPTER_NAME = "CSV";
    @SuppressWarnings("WeakerAccess")
    public static final String DESCRIPTION = "An adapter for querying CSV files. The location of the directory containing the CSV files can be specified. Currently, this adapter only supports read operations.";
    @SuppressWarnings("WeakerAccess")
    public static final List<AdapterSetting> AVAILABLE_SETTINGS = ImmutableList.of(
            new AdapterSettingString( "directory", false, true, true, "testTestCsv" )
    );

    private URL csvDir;
    private CsvSchema currentSchema;


    public CsvStore( final int storeId, final String uniqueName, final Map<String, String> settings ) {
        super( storeId, uniqueName, settings, true );
        setCsvDir( settings );
    }


    private void setCsvDir( Map<String, String> settings ) {
        String dir = settings.get( "directory" );
        if ( dir.startsWith( "classpath://" ) ) {
            URL url = this.getClass().getClassLoader().getResource( dir.replace( "classpath://", "" ) + "/" );
            csvDir = url;
        } else {
            try {
                csvDir = new File( dir ).toURI().toURL();
            } catch ( MalformedURLException e ) {
                throw new RuntimeException( e );
            }
        }
    }


    @Override
    public void createNewSchema( SchemaPlus rootSchema, String name ) {
        currentSchema = new CsvSchema( csvDir, Flavor.SCANNABLE );
    }


    @Override
    public Table createTableSchema( CatalogTable catalogTable, List<CatalogColumnPlacement> columnPlacementsOnStore ) {
        return currentSchema.createCsvTable( catalogTable, columnPlacementsOnStore, this );
    }


    @Override
    public Schema getCurrentSchema() {
        return currentSchema;
    }


    @Override
    public void createTable( Context context, CatalogTable catalogTable ) {
        throw new RuntimeException( "CSV adapter does not support creating table" );
    }


    @Override
    public void dropTable( Context context, CatalogTable catalogTable ) {
        log.warn( "CSV adapter does not support dropping tables!" );
    }


    @Override
    public void addColumn( Context context, CatalogTable catalogTable, CatalogColumn catalogColumn ) {
        log.warn( "CSV adapter does not support adding columns!" );
    }


    @Override
    public void dropColumn( Context context, CatalogColumnPlacement columnPlacement ) {
        log.warn( "CSV adapter does not support dropping columns!" );
    }


    @Override
    public void addIndex( Context context, CatalogIndex catalogIndex ) {
        throw new RuntimeException( "CSV adapter does not support adding indexes" );
    }


    @Override
    public void dropIndex( Context context, CatalogIndex catalogIndex ) {
        throw new RuntimeException( "CSV adapter does not support dropping indexes" );
    }


    @Override
    public boolean prepare( PolyXid xid ) {
        log.debug( "CSV Store does not support prepare()." );
        return true;
    }


    @Override
    public void commit( PolyXid xid ) {
        log.debug( "CSV Store does not support commit()." );
    }


    @Override
    public void rollback( PolyXid xid ) {
        log.debug( "CSV Store does not support rollback()." );
    }


    @Override
    public void truncate( Context context, CatalogTable table ) {
        log.warn( "CSV Store does not support truncate." );
    }


    @Override
    public void updateColumnType( Context context, CatalogColumnPlacement placement, CatalogColumn catalogColumn ) {
        throw new RuntimeException( "CSV adapter does not support updating column types!" );
    }


    @Override
    public String getAdapterName() {
        return ADAPTER_NAME;
    }


    @Override
    public List<AdapterSetting> getAvailableSettings() {
        return AVAILABLE_SETTINGS;
    }


    @Override
    public List<AvailableIndexMethod> getAvailableIndexMethods() {
        return new ArrayList<>();
    }


    @Override
    public AvailableIndexMethod getDefaultIndexMethod() {
        throw new RuntimeException( "CSV adapter does not support adding indexes" );
    }


    @Override
    public List<FunctionalIndexInfo> getFunctionalIndexes( CatalogTable catalogTable ) {
        return ImmutableList.of();
    }


    @Override
    public void shutdown() {
        // Nothing to do
    }


    @Override
    protected void reloadSettings( List<String> updatedSettings ) {
        if ( updatedSettings.contains( "directory" ) ) {
            setCsvDir( settings );
        }
    }

}

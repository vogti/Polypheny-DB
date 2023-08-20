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

package org.polypheny.db.catalog.snapshot.impl;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogAdapter;
import org.polypheny.db.catalog.entity.CatalogQueryInterface;
import org.polypheny.db.catalog.entity.CatalogUser;
import org.polypheny.db.catalog.entity.logical.LogicalEntity;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.logistic.Pattern;
import org.polypheny.db.catalog.snapshot.AllocSnapshot;
import org.polypheny.db.catalog.snapshot.LogicalDocSnapshot;
import org.polypheny.db.catalog.snapshot.LogicalGraphSnapshot;
import org.polypheny.db.catalog.snapshot.LogicalRelSnapshot;
import org.polypheny.db.catalog.snapshot.Snapshot;

@Value
@Accessors(fluent = true)
public class SnapshotImpl implements Snapshot {


    LogicalRelSnapshot rel;
    LogicalDocSnapshot doc;
    LogicalGraphSnapshot graph;
    AllocSnapshot alloc;
    @Getter
    long id;
    ImmutableMap<Long, CatalogUser> users;

    ImmutableMap<String, CatalogUser> userNames;
    ImmutableMap<Long, CatalogQueryInterface> interfaces;

    ImmutableMap<String, CatalogQueryInterface> interfaceNames;
    ImmutableMap<Long, CatalogAdapter> adapters;

    ImmutableMap<String, CatalogAdapter> adapterNames;

    ImmutableMap<Long, LogicalNamespace> namespaces;

    ImmutableMap<String, LogicalNamespace> namespaceNames;


    public SnapshotImpl( long id, Catalog catalog, Map<Long, LogicalNamespace> namespaces, LogicalRelSnapshot rel, LogicalDocSnapshot doc, LogicalGraphSnapshot graph, AllocSnapshot alloc ) {
        this.id = id;
        this.rel = rel;
        this.doc = doc;
        this.graph = graph;

        this.namespaces = ImmutableMap.copyOf( namespaces );

        this.namespaceNames = ImmutableMap.copyOf( namespaces.values().stream().collect( Collectors.toMap( n -> n.caseSensitive ? n.name : n.name.toLowerCase(), n -> n ) ) );

        this.alloc = alloc;

        this.users = ImmutableMap.copyOf( catalog.getUsers() );
        this.userNames = ImmutableMap.copyOf( users.values().stream().collect( Collectors.toMap( u -> u.name, u -> u ) ) );
        this.interfaces = ImmutableMap.copyOf( catalog.getInterfaces() );
        this.interfaceNames = ImmutableMap.copyOf( interfaces.values().stream().collect( Collectors.toMap( i -> i.name, i -> i ) ) );
        this.adapters = ImmutableMap.copyOf( catalog.getAdapters() );
        this.adapterNames = ImmutableMap.copyOf( adapters.values().stream().collect( Collectors.toMap( a -> a.uniqueName, a -> a ) ) );
    }


    @Override
    public @NotNull List<LogicalNamespace> getNamespaces( @Nullable Pattern name ) {
        if ( name == null ) {
            return namespaces.values().asList();
        }
        return namespaces.values().stream().filter( n -> n.caseSensitive ? n.name.matches( name.toRegex() ) : n.name.toLowerCase().matches( name.toLowerCase().toRegex() ) ).collect( Collectors.toList() );
    }


    @Override
    public @NotNull Optional<LogicalNamespace> getNamespace( long id ) {
        return Optional.ofNullable( namespaces.get( id ) );
    }


    @Override
    public @NotNull Optional<LogicalNamespace> getNamespace( String name ) {
        LogicalNamespace namespace = namespaceNames.get( name );

        if ( namespace != null ) {
            return Optional.of( namespace );
        }
        namespace = namespaceNames.get( name.toLowerCase() );

        if ( namespace != null && !namespace.caseSensitive ) {
            return Optional.of( namespace );
        }

        return Optional.empty();
    }

    @Override
    public @NotNull Optional<CatalogUser> getUser( String name ) {
        return Optional.ofNullable( userNames.get( name ) );
    }


    @Override
    public @NotNull Optional<CatalogUser> getUser( long id ) {
        return Optional.ofNullable( users.get( id ) );
    }


    @Override
    public List<CatalogAdapter> getAdapters() {
        return adapters.values().asList();
    }


    @Override
    public @NotNull Optional<CatalogAdapter> getAdapter( String uniqueName ) {
        return Optional.ofNullable( adapterNames.get( uniqueName ) );
    }


    @Override
    public @NotNull Optional<CatalogAdapter> getAdapter( long id ) {
        return Optional.ofNullable( adapters.get( id ) );
    }



    @Override
    public List<CatalogQueryInterface> getQueryInterfaces() {
        return interfaces.values().asList();
    }


    @Override
    public Optional<CatalogQueryInterface> getQueryInterface( String uniqueName ) {
        return Optional.ofNullable( interfaceNames.get( uniqueName ) );
    }


    @Override
    public Optional<CatalogQueryInterface> getQueryInterface( long id ) {
        return Optional.ofNullable( interfaces.get( id ) );
    }


    @Override
    public List<LogicalTable> getTablesForPeriodicProcessing() {
        return null;
    }


    @Override
    public Optional<? extends LogicalEntity> getLogicalEntity( long id ) {
        if ( rel.getTable( id ).isPresent() ) {
            return rel.getTable( id );
        }

        if ( doc.getCollection( id ).isPresent() ) {
            return doc.getCollection( id );
        }

        return graph.getGraph( id );
    }


}

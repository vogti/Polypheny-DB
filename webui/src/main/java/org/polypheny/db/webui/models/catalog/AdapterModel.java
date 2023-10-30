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

package org.polypheny.db.webui.models.catalog;


import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.Nullable;
import org.polypheny.db.adapter.Adapter;
import org.polypheny.db.adapter.AdapterManager;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.adapter.DataStore.IndexMethodModel;
import org.polypheny.db.adapter.DeployMode;
import org.polypheny.db.catalog.entity.LogicalAdapter;
import org.polypheny.db.catalog.entity.LogicalAdapter.AdapterType;


@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class AdapterModel extends IdEntity {

    public String adapterName;
    public AdapterType type;
    public Map<String, AdapterSettingValueModel> settings;
    public DeployMode mode;
    public List<IndexMethodModel> indexMethods;


    public AdapterModel(
            @Nullable Long id,
            @Nullable String name,
            String adapterName,
            AdapterType type,
            Map<String, AdapterSettingValueModel> settings,
            DeployMode mode,
            List<IndexMethodModel> indexMethods ) {
        super( id, name );
        this.adapterName = adapterName;
        this.type = type;
        this.settings = settings;
        this.mode = mode;
        this.indexMethods = indexMethods;
    }


    public static AdapterModel from( LogicalAdapter adapter ) {
        Map<String, AdapterSettingValueModel> settings = adapter.settings.entrySet().stream().collect( Collectors.toMap( Entry::getKey, s -> AdapterSettingValueModel.from( s.getKey(), s.getValue() ) ) );

        Adapter<?> a = AdapterManager.getInstance().getAdapter( adapter.id );
        return new AdapterModel(
                adapter.id,
                adapter.uniqueName,
                adapter.adapterName,
                adapter.type,
                settings,
                adapter.mode,
                adapter.type == AdapterType.STORE ? ((DataStore<?>) a).getAvailableIndexMethods() : List.of() );
    }


    @Value
    public static class AdapterSettingValueModel {

        String name;
        String value;


        public static AdapterSettingValueModel from( String name, String value ) {
            return new AdapterSettingValueModel( name, value );
        }

    }

}

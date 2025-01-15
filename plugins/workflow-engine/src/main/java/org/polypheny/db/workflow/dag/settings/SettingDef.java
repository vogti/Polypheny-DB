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
 */

package org.polypheny.db.workflow.dag.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Value;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.util.Wrapper;
import org.polypheny.db.workflow.dag.activities.ActivityException.InvalidSettingException;
import org.polypheny.db.workflow.dag.annotations.ActivityDefinition;
import org.polypheny.db.workflow.dag.annotations.BoolSetting;
import org.polypheny.db.workflow.dag.annotations.DoubleSetting;
import org.polypheny.db.workflow.dag.annotations.EntitySetting;
import org.polypheny.db.workflow.dag.annotations.IntSetting;
import org.polypheny.db.workflow.dag.annotations.StringSetting;

@Getter
public abstract class SettingDef {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SettingType type;
    private final String key;
    private final String displayName;
    private final String shortDescription;
    private final String longDescription;

    @JsonIgnore // the UI does not handle defaultValues. This is instead done by the backend, at the moment the activity is created.
    private final SettingValue defaultValue;

    private final String group;
    private final String subgroup;
    private final int position;

    /**
     * The subPointer is used to specify conditional rendering of this setting in the UI based on the value
     * of another setting. An empty pointer disables conditional rendering for this setting.
     * <p>
     * SubPointer is a JsonPointer (<a href="https://datatracker.ietf.org/doc/html/rfc6901">RFC6901</a>) to a setting value.
     * Note that "/" and "~" need to be escaped as ~1 and ~0 respectively.
     * The leading "/" is optional in the constructor, as the first referenced object must always correspond to a setting name.
     * Examples of valid pointers:
     * <ul>
     *     <li>{@code "mySetting"}</li>
     *     <li>{@code "/mySetting"}</li>
     *     <li>{@code "mySetting/names"}</li>
     *     <li>{@code "mySetting/names/0"}</li>
     *     <li>{@code "my~1escaped~0setting~1example"}</li>
     * </ul>
     */
    private final String subPointer;

    /**
     * SubValues is an array of values to compare the object at {@link #getSubPointer()} to.
     * The setting is only rendered if at least one object is equal to a value of this array.
     * The SettingDef constructor expects values specified as json. This implies that strings must be quoted!
     */
    private final List<JsonNode> subValues;


    public SettingDef(
            SettingType type, String key, String displayName,
            String shortDescription, String longDescription,
            SettingValue defaultValue, String group, String subgroup,
            int position, String subPointer, String[] subValues ) {
        this.type = type;
        this.key = key;
        this.displayName = displayName.isBlank() ? key : displayName;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription.isEmpty() ? shortDescription : longDescription;
        this.defaultValue = defaultValue;
        this.group = group;
        this.subgroup = subgroup;
        this.position = position;
        this.subPointer = (subPointer.isEmpty() || subPointer.startsWith( "/" )) ? subPointer : "/" + subPointer;
        this.subValues = Arrays.stream( subValues ).map( v -> {
            try {
                return MAPPER.readTree( v );
            } catch ( JsonProcessingException e ) {
                throw new GenericRuntimeException( "Invalid subValue for setting " + key + ": " + v, e );
            }
        } ).toList();
    }


    /**
     * Builds a {@link SettingValue} from the given {@link JsonNode} that represents a value for this SettingInfo.
     *
     * @param node the {@link JsonNode} containing the data (with any variable references already replaced) to build the {@link SettingValue}.
     * @return the constructed {@link SettingValue}.
     * @throws IllegalArgumentException if the {@link JsonNode} has an unexpected format.
     */
    public abstract SettingValue buildValue( JsonNode node );

    /**
     * Validates that the specified value is valid for this SettingDef.
     *
     * @param value the value to validate
     * @throws InvalidSettingException if the setting value is invalid
     * @throws IllegalArgumentException if the setting value has the wrong SettingValue class
     */
    public abstract void validateValue( SettingValue value ) throws InvalidSettingException;


    /**
     * Builds a {@link SettingValue} from the given {@link JsonNode} and validates itthat represents a value for this SettingInfo.
     *
     * @param node the {@link JsonNode} containing the data (with any variable references already replaced) to build the {@link SettingValue}.
     * @return the constructed {@link SettingValue}.
     * @throws IllegalArgumentException if the {@link JsonNode} has an unexpected format
     * @throws InvalidSettingException if the setting value is invalid
     */
    public SettingValue buildValidatedValue( JsonNode node ) throws InvalidSettingException {
        SettingValue value = buildValue( node );
        validateValue( value );
        return value;
    }


    public static List<SettingDef> fromAnnotations( Annotation[] annotations ) {
        List<SettingDef> settings = new ArrayList<>();

        for ( Annotation annotation : annotations ) {
            if ( annotation instanceof ActivityDefinition ) {
                continue; // activity definition is treated separately
            }

            // Register setting annotations here
            if ( annotation instanceof StringSetting a ) {
                settings.add( new StringSettingDef( a ) );
            } else if ( annotation instanceof StringSetting.List a ) {
                Arrays.stream( a.value() ).forEach( el -> settings.add( new StringSettingDef( el ) ) );
            } else if ( annotation instanceof IntSetting a ) {
                settings.add( new IntSettingDef( a ) );
            } else if ( annotation instanceof IntSetting.List a ) {
                Arrays.stream( a.value() ).forEach( el -> settings.add( new IntSettingDef( el ) ) );
            } else if ( annotation instanceof EntitySetting a ) {
                settings.add( new EntitySettingDef( a ) );
            } else if ( annotation instanceof EntitySetting.List a ) {
                Arrays.stream( a.value() ).forEach( el -> settings.add( new EntitySettingDef( el ) ) );
            } else if ( annotation instanceof BoolSetting a ) {
                settings.add( new BoolSettingDef( a ) );
            } else if ( annotation instanceof BoolSetting.List a ) {
                Arrays.stream( a.value() ).forEach( el -> settings.add( new BoolSettingDef( el ) ) );
            } else if ( annotation instanceof DoubleSetting a ) {
                settings.add( new DoubleSettingDef( a ) );
            } else if ( annotation instanceof DoubleSetting.List a ) {
                Arrays.stream( a.value() ).forEach( el -> settings.add( new DoubleSettingDef( el ) ) );
            }
        }
        return settings;
    }


    /**
     * Steps for adding a new {@code SettingType}:
     * <ol>
     *     <li>Introduce new {@link SettingType} enum</li>
     *     <li>Create Annotation</li>
     *     <li>Either extend {@link SettingValue} or express value as a composition of existing {@code SettingValue}s</li>
     *     <li>Extend {@link SettingDef}</li>
     *     <li>Register the created annotation and {@link SettingDef} in {@link SettingDef#fromAnnotations}</li>
     *     <li>(Introduce the new SettingType to Polypheny-UI)</li>
     * </ol>
     */
    public enum SettingType {
        STRING,
        INT,
        ENTITY,
        BOOLEAN,
        DOUBLE
    }


    public interface SettingValue extends Wrapper {

        JsonNode toJson( ObjectMapper mapper );

    }


    @Value
    public static class Settings {

        Map<String, SettingValue> map;


        public Settings( Map<String, SettingValue> map ) {
            this.map = Collections.unmodifiableMap( map );
        }


        public <T extends SettingValue> T get( String key, Class<T> clazz ) {
            return map.get( key ).unwrapOrThrow( clazz );
        }


        public SettingValue get( String key ) {
            return map.get( key );
        }


        public Map<String, JsonNode> getSerializableSettings() {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, JsonNode> settingValues = new HashMap<>();
            for ( Entry<String, SettingValue> entry : map.entrySet() ) {
                settingValues.put( entry.getKey(), entry.getValue().toJson( mapper ) );
            }
            return Collections.unmodifiableMap( settingValues );
        }

    }


    @Value
    public static class SettingsPreview {

        Map<String, Optional<SettingValue>> map;


        public SettingsPreview( Map<String, Optional<SettingValue>> map ) {
            this.map = Collections.unmodifiableMap( map );
        }


        public static SettingsPreview of( Settings settings ) {
            Map<String, Optional<SettingValue>> map = settings.map.entrySet().stream()
                    .collect( Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> Optional.ofNullable( entry.getValue() )
                    ) );
            return new SettingsPreview( map );
        }


        public <T extends SettingValue> Optional<T> get( String key, Class<T> clazz ) {
            return map.get( key ).map( value -> value.unwrapOrThrow( clazz ) );
        }


        public Optional<SettingValue> get( String key ) {
            return map.get( key );
        }

    }

}
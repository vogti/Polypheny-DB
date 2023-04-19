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

package org.polypheny.db.catalog.util;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.polypheny.db.catalog.IdBuilder;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.entity.logical.LogicalEntity;
import org.polypheny.db.catalog.entity.physical.PhysicalEntity;

@Data
public class PhysicalContext {

    @Builder.Default
    IdBuilder idBuilder = IdBuilder.getInstance();


    List<LogicalEntity> logicals = new ArrayList<>();
    List<AllocationEntity> allocations = new ArrayList<>();
    List<? extends PhysicalEntity> physicals = new ArrayList<>();


}

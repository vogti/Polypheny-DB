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

package org.polypheny.db.workflow.dag.activities.impl;

import java.util.List;
import java.util.Optional;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.plan.AlgCluster;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.workflow.dag.activities.Activity;
import org.polypheny.db.workflow.dag.activities.Activity.ActivityCategory;
import org.polypheny.db.workflow.dag.activities.Activity.PortType;
import org.polypheny.db.workflow.dag.activities.ActivityException;
import org.polypheny.db.workflow.dag.activities.Fusable;
import org.polypheny.db.workflow.dag.activities.Pipeable;
import org.polypheny.db.workflow.dag.annotations.ActivityDefinition;
import org.polypheny.db.workflow.dag.annotations.ActivityDefinition.InPort;
import org.polypheny.db.workflow.dag.annotations.ActivityDefinition.OutPort;
import org.polypheny.db.workflow.dag.settings.SettingDef.Settings;
import org.polypheny.db.workflow.dag.settings.SettingDef.SettingsPreview;
import org.polypheny.db.workflow.engine.execution.context.ExecutionContext;
import org.polypheny.db.workflow.engine.execution.context.PipeExecutionContext;
import org.polypheny.db.workflow.engine.execution.pipe.InputPipe;
import org.polypheny.db.workflow.engine.execution.pipe.OutputPipe;
import org.polypheny.db.workflow.engine.storage.reader.CheckpointReader;
import org.polypheny.db.workflow.engine.storage.reader.DocReader;
import org.polypheny.db.workflow.engine.storage.writer.DocWriter;

@ActivityDefinition(type = "docIdentity", displayName = "Document Identity", categories = { ActivityCategory.TRANSFORM, ActivityCategory.DOCUMENT },
        inPorts = { @InPort(type = PortType.DOC) },
        outPorts = { @OutPort(type = PortType.DOC) }
)
@SuppressWarnings("unused")
public class DocIdentityActivity implements Activity, Fusable, Pipeable {


    public DocIdentityActivity() {
    }


    @Override
    public List<Optional<AlgDataType>> previewOutTypes( List<Optional<AlgDataType>> inTypes, SettingsPreview settings ) throws ActivityException {
        return List.of( inTypes.get( 0 ) );
    }


    @Override
    public void execute( List<CheckpointReader> inputs, Settings settings, ExecutionContext ctx ) throws Exception {
        DocReader input = (DocReader) inputs.get( 0 );
        DocWriter output = ctx.createDocWriter( 0 );
        output.write( input.getIterator() );
    }


    @Override
    public AlgDataType lockOutputType( List<AlgDataType> inTypes, Settings settings ) throws Exception {
        return inTypes.get( 0 );
    }


    @Override
    public void pipe( List<InputPipe> inputs, OutputPipe output, Settings settings, PipeExecutionContext ctx ) throws Exception {
        for ( List<PolyValue> value : inputs.get( 0 ) ) {
            output.put( value );
        }
    }


    @Override
    public AlgNode fuse( List<AlgNode> inputs, Settings settings, AlgCluster cluster ) throws Exception {
        return inputs.get( 0 ); // this does not really test fusion
    }


    @Override
    public void reset() {
    }

}
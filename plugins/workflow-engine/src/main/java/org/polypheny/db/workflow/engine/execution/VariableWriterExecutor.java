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

package org.polypheny.db.workflow.engine.execution;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.workflow.dag.Workflow;
import org.polypheny.db.workflow.dag.activities.ActivityWrapper;
import org.polypheny.db.workflow.dag.activities.VariableWriter;
import org.polypheny.db.workflow.dag.settings.SettingDef.SettingValue;
import org.polypheny.db.workflow.engine.execution.context.ExecutionContextImpl;
import org.polypheny.db.workflow.engine.storage.CheckpointReader;
import org.polypheny.db.workflow.engine.storage.StorageManager;

public class VariableWriterExecutor extends Executor {

    private final ActivityWrapper wrapper;
    private ExecutionContextImpl ctx;


    protected VariableWriterExecutor( StorageManager sm, Workflow workflow, UUID activityId ) {
        super( sm, workflow );
        this.wrapper = workflow.getActivity( activityId );
    }


    @Override
    void execute() throws ExecutorException {
        List<CheckpointReader> inputs = getReaders( wrapper );
        List<AlgDataType> inputTypes = inputs.stream().map( CheckpointReader::getTupleType ).toList();

        mergeInputVariables( wrapper.getId() );
        Map<String, SettingValue> settings = wrapper.resolveSettings(); // settings before variable update
        ctx = new ExecutionContextImpl( wrapper, sm );

        try ( CloseableList ignored = new CloseableList( inputs ) ) {
            VariableWriter activity = (VariableWriter) wrapper.getActivity();
            // we skip activity.updateVariables() on purpose, since variable updates are performed within execute
            activity.execute( inputs, settings, ctx, wrapper.getVariables() );
        } catch ( Exception e ) {
            throw new ExecutorException( e );
        }
    }


    @Override
    public void interrupt() {
        if ( ctx != null ) {
            ctx.setInterrupted();
        }

    }

}

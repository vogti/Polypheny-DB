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

package org.polypheny.db.workflow.session;

import java.util.UUID;
import org.polypheny.db.workflow.dag.Workflow;

public class UserSession extends WorkflowSession {

    private final UUID workflowId;
    private final int version;


    public UserSession( Workflow wf, UUID workflowId, int version ) {
        super( wf );
        this.workflowId = workflowId;
        this.version = version;
    }

}

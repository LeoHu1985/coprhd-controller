/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
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
 *
 */
package com.emc.sa.model.dao;

import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.OEWorkflow;
import com.google.common.collect.Lists;

public class OrchestrationWorkflowFinder extends ModelFinder<OEWorkflow> {

    public OrchestrationWorkflowFinder(DBClientWrapper client) {
        super(OEWorkflow.class, client);
    }
    
    public List<OEWorkflow> findByName(final String name) {

        List<OEWorkflow> results = Lists.newArrayList();

        List<NamedElement> workflows = client.findByAlternateId(OEWorkflow.class, OEWorkflow.NAME, name);
        if (workflows != null) {
            results.addAll(findByIds(toURIs(workflows)));
        }

        return results;
    }

}

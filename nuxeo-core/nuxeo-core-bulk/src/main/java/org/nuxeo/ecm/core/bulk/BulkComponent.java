/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     pierre
 */
package org.nuxeo.ecm.core.bulk;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * The bulk component.
 *
 * @since 10.2
 */
public class BulkComponent extends DefaultComponent implements BulkAdminService {

    public static final String BULK_LOG_MANAGER_NAME = "bulk";

    public static final String BULK_KV_STORE_NAME = "bulk";

    public static final String ACTIONS_XP = "actions";

    protected Queue<BulkActionDescriptor> actionsRegistry = new LinkedList<>();

    protected BulkService bulkService;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter.isAssignableFrom(BulkService.class)) {
            return (T) bulkService;
        } else if (adapter.isAssignableFrom(BulkAdminService.class)) {
            // BulkAdminService is implemented by the component and not as a service like BulkService because
            // StreamBulkScroller needs bulk configuration during its initialization. Its initialization happens during
            // StreamService's start step which is before BulkComponent's start step, so at a moment where services are
            // not yet created.
            return (T) this;
        }
        return null;
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (ACTIONS_XP.equals(extensionPoint)) {
            actionsRegistry.add((BulkActionDescriptor) contribution);
        } else {
            throw new NuxeoException("Unknown extension point: " + extensionPoint);
        }
    }

    @Override
    public void start(ComponentContext context) {
        bulkService = new BulkServiceImpl();
    }

    @Override
    public void stop(ComponentContext context) {
        bulkService = null;
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (ACTIONS_XP.equals(extensionPoint)) {
            actionsRegistry.remove(contribution);
        } else {
            throw new NuxeoException("Unknown extension point: " + extensionPoint);
        }
    }

    // ---------------------
    // BulkAdminService part
    // ---------------------

    @Override
    public List<String> getActions() {
        return actionsRegistry.stream().map(BulkActionDescriptor::getName).collect(Collectors.toList());
    }
}

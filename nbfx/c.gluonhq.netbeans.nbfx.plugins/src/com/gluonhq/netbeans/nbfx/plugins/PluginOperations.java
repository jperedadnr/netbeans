/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.plugins;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import org.netbeans.api.autoupdate.InstallSupport;
import org.netbeans.api.autoupdate.OperationContainer;
import org.netbeans.api.autoupdate.OperationException;
import org.netbeans.api.autoupdate.OperationSupport;
import org.netbeans.api.autoupdate.UpdateElement;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Runs plugin operations through the headless autoupdate API: install/update go through
 * download, validation and install; uninstall, activate and deactivate go through a direct
 * operation. The work runs off the FX thread; a {@link OperationSupport.Restarter} means the
 * changes are applied at the next restart.
 *
 * @since 1.0
 */
final class PluginOperations {

    private static final Logger LOG = Logger.getLogger(PluginOperations.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nbfx-plugins-operation", 1);

    private PluginOperations() {
    }

    /** Installs or updates {@code elements}. */
    static void installOrUpdate(OperationContainer<InstallSupport> container,
            Collection<UpdateElement> elements, Runnable onDone) {
        container.add(elements);
        run(onDone, () -> {
            InstallSupport support = container.getSupport();
            InstallSupport.Validator validator = support.doDownload(null, false, true);
            InstallSupport.Installer installer = support.doValidate(validator, null);
            OperationSupport.Restarter restarter = support.doInstall(installer, null);
            if (restarter != null) {
                support.doRestartLater(restarter);
                notifyRestartLater();
            }
        });
    }

    /** Uninstalls {@code elements}. */
    static void uninstall(OperationContainer<OperationSupport> container,
            Collection<UpdateElement> elements, Runnable onDone) {
        container.add(elements);
        run(onDone, () -> restartLater(container.getSupport()));
    }

    /** Activates {@code elements}. */
    static void enable(OperationContainer<OperationSupport> container,
            Collection<UpdateElement> elements, Runnable onDone) {
        container.add(elements);
        run(onDone, () -> restartLater(container.getSupport()));
    }

    /** Deactivates {@code elements}. */
    static void disable(OperationContainer<OperationSupport> container,
            Collection<UpdateElement> elements, Runnable onDone) {
        container.add(elements);
        run(onDone, () -> restartLater(container.getSupport()));
    }

    private static void restartLater(OperationSupport support) throws OperationException {
        OperationSupport.Restarter restarter = support.doOperation(null);
        if (restarter != null) {
            support.doRestartLater(restarter);
            notifyRestartLater();
        }
    }

    private static void notifyRestartLater() {
        Platform.runLater(() -> PluginDialogs.info(
                NbBundle.getMessage(PluginOperations.class, "Plugins.restartLater")));
    }

    private static void run(Runnable onDone, CheckedRunnable operation) {
        RP.post(() -> {
            try {
                operation.run();
            } catch (OperationException ex) {
                LOG.log(Level.WARNING, "Plugin operation failed", ex);
                Platform.runLater(() -> PluginDialogs.error(ex.getLocalizedMessage()));
            } finally {
                Platform.runLater(onDone);
            }
        });
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws OperationException;
    }
}

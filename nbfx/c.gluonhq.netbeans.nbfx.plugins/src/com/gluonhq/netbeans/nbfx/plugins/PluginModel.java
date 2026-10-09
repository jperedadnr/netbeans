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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.netbeans.api.autoupdate.UpdateElement;
import org.netbeans.api.autoupdate.UpdateManager;
import org.netbeans.api.autoupdate.UpdateUnit;
import org.openide.util.RequestProcessor;

/**
 * The Plugins dialog's data: the module units split into the three tabs and reloaded off the FX
 * thread.
 *
 * @since 1.0
 */
final class PluginModel {

    private static final Logger LOG = Logger.getLogger(PluginModel.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nbfx-plugins", 1);

    private final ObservableList<PluginRow> updates = FXCollections.observableArrayList();
    private final ObservableList<PluginRow> available = FXCollections.observableArrayList();
    private final ObservableList<PluginRow> installed = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(true);

    ObservableList<PluginRow> updates() {
        return updates;
    }

    ObservableList<PluginRow> available() {
        return available;
    }

    ObservableList<PluginRow> installed() {
        return installed;
    }

    BooleanProperty loadingProperty() {
        return loading;
    }

    /** Reads the module units off the FX thread and fills the three lists. */
    void load(Runnable onDone) {
        loading.set(true);
        RP.post(() -> {
            List<PluginRow> updateRows = new ArrayList<>();
            List<PluginRow> availableRows = new ArrayList<>();
            List<PluginRow> installedRows = new ArrayList<>();
            try {
                for (UpdateUnit unit : UpdateManager.getDefault().getUpdateUnits(UpdateManager.TYPE.MODULE)) {
                    UpdateElement installedElement = unit.getInstalled();
                    List<UpdateElement> candidates = unit.getAvailableUpdates();
                    if (installedElement == null) {
                        if (!candidates.isEmpty()) {
                            availableRows.add(new PluginRow(unit, null, newest(candidates)));
                        }
                    } else {
                        installedRows.add(new PluginRow(unit, installedElement, installedElement));
                        if (!candidates.isEmpty()) {
                            updateRows.add(new PluginRow(unit, installedElement, newest(candidates)));
                        }
                    }
                }
            } catch (RuntimeException | LinkageError ex) {
                LOG.log(Level.WARNING, "Cannot list the modules", ex);
            }
            sort(updateRows);
            sort(availableRows);
            sort(installedRows);
            Platform.runLater(() -> {
                updates.setAll(updateRows);
                available.setAll(availableRows);
                installed.setAll(installedRows);
                loading.set(false);
                if (onDone != null) {
                    onDone.run();
                }
            });
        });
    }

    private static UpdateElement newest(List<UpdateElement> elements) {
        UpdateElement newest = elements.get(0);
        for (UpdateElement element : elements) {
            if (compareVersions(element.getSpecificationVersion(), newest.getSpecificationVersion()) > 0) {
                newest = element;
            }
        }
        return newest;
    }

    private static void sort(List<PluginRow> rows) {
        rows.sort(Comparator
                .comparing(PluginRow::category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PluginRow::name, String.CASE_INSENSITIVE_ORDER));
    }

    private static int compareVersions(String left, String right) {
        if (left == null || right == null) {
            return 0;
        }
        return left.compareTo(right);
    }
}

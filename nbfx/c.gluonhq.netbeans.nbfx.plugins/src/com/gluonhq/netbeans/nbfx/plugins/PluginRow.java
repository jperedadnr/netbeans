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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.netbeans.api.autoupdate.UpdateElement;
import org.netbeans.api.autoupdate.UpdateUnit;

/**
 * One row of a plugins table: a module and the element an action applies to.
 * <p>
 * For the Installed tab {@link #candidate()} is the installed element; for the Available and
 * Updates tabs it is the element to install or update to. The checkbox selection is a JavaFX
 * property so a {@code CheckBoxTableCell} can bind to it.
 *
 * @since 1.0
 */
final class PluginRow {

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final UpdateUnit unit;
    private final UpdateElement installed;
    private final UpdateElement candidate;

    PluginRow(UpdateUnit unit, UpdateElement installed, UpdateElement candidate) {
        this.unit = unit;
        this.installed = installed;
        this.candidate = candidate;
    }

    BooleanProperty selectedProperty() {
        return selected;
    }

    boolean isSelected() {
        return selected.get();
    }

    UpdateUnit unit() {
        return unit;
    }

    UpdateElement installed() {
        return installed;
    }

    UpdateElement candidate() {
        return candidate;
    }

    String name() {
        return candidate.getDisplayName();
    }

    String category() {
        return nullToEmpty(candidate.getCategory());
    }

    String version() {
        return nullToEmpty(candidate.getSpecificationVersion());
    }

    String installedVersion() {
        return installed == null ? "" : nullToEmpty(installed.getSpecificationVersion());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

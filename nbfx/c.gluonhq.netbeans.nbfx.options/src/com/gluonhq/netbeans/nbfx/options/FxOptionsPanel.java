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
package com.gluonhq.netbeans.nbfx.options;

import javafx.scene.Node;

/**
 * One panel of the Options dialog. Panels are registered with {@code @FxOptionsRegistration} and
 * discovered through {@link OptionsRegistry}.
 *
 * @since 1.0
 */
public interface FxOptionsPanel {

    /** The stable identifier of the panel; must match {@code @FxOptionsRegistration#id()}. */
    String getId();

    /** The panel's display name, shown in the category list. */
    String getDisplayName();

    /** The category the panel belongs to. */
    default String getCategory() {
        return "General";
    }

    /** The position of the panel relative to the others. */
    default int getPosition() {
        return Integer.MAX_VALUE;
    }

    /** The panel's JavaFX component. Called on the JavaFX Application Thread. */
    Node getComponent();

    /** Loads the current values into the component, before it is shown. */
    default void load() {
    }

    /** Persists the panel's values. Called when the user presses OK or Apply. */
    default void apply() {
    }

    /** Discards the panel's values. Called when the user presses Cancel. */
    default void cancel() {
    }

    /** Whether the panel's values are valid. */
    default boolean isValid() {
        return true;
    }
}

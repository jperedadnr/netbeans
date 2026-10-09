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

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.openide.util.NbBundle;

/**
 * The JavaFX alerts the Plugins dialog uses.
 *
 * @since 1.0
 */
final class PluginDialogs {

    private PluginDialogs() {
    }

    static void error(String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(message("Plugins.title"));
        alert.setHeaderText(null);
        alert.setContentText(detail == null || detail.isBlank() ? message("Plugins.error") : detail);
        alert.showAndWait();
    }

    static void info(String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(message("Plugins.title"));
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }

    static boolean confirm(String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(message("Plugins.title"));
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static String message(String key) {
        return NbBundle.getMessage(PluginDialogs.class, key);
    }
}

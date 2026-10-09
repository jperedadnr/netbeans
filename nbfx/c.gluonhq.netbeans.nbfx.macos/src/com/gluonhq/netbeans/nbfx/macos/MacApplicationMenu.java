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
package com.gluonhq.netbeans.nbfx.macos;

import com.gluonhq.netbeans.nbfx.api.FxApplicationMenu;
import java.awt.Desktop;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import org.openide.util.lookup.ServiceProvider;

/**
 * The macOS application menu (Preferences… and Quit).
 * <p>
 * <b>Quarantine.</b> The macOS application menu is owned by the OS, and JavaFX has no API to add
 * items to it: {@code MenuBar.setUseSystemMenuBar(true)} only mirrors the MenuBar's own menus. The
 * only supported mechanism is the AWT {@link Desktop} handlers (the same ones NetBeans' applemenu
 * module uses); no Swing is involved, and the handlers only route to the pure-FX Options dialog and
 * the normal exit path. This module is enabled only on macOS (see
 * {@code OpenIDE-Module-Requires: org.openide.modules.os.MacOSX}), so it never loads elsewhere.
 *
 * @since 1.0
 */
@ServiceProvider(service = FxApplicationMenu.class)
public final class MacApplicationMenu implements FxApplicationMenu {

    private static final Logger LOG = Logger.getLogger(MacApplicationMenu.class.getName());

    @Override
    public void install(Runnable openPreferences, Runnable quit) {
        try {
            Desktop desktop = Desktop.getDesktop();
            desktop.setPreferencesHandler(event -> Platform.runLater(openPreferences));
            desktop.setQuitHandler((event, response) -> {
                // Let the application confirm unsaved changes and exit itself.
                response.cancelQuit();
                Platform.runLater(quit);
            });
        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "Could not register the macOS application-menu handlers", ex);
        }
    }
}

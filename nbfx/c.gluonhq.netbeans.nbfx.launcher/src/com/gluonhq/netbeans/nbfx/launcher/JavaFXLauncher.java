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
package com.gluonhq.netbeans.nbfx.launcher;

import com.gluonhq.netbeans.nbfx.ui.JavaFXLaunchApp;
import com.gluonhq.netbeans.nbfx.ui.project.VersioningOptOut;
import java.util.logging.Logger;
import javafx.application.Application;
import org.openide.modules.ModuleInstall;

/**
 * Starts the JavaFX application after the NetBeans Platform has loaded.
 * <p>
 * The platform still starts its Swing window system; {@link SwingWindowSuppressor} hides its main
 * window, which the JavaFX frontend does not use. When the window system is fully replaced, that
 * suppression (and the platform window-system modules it depends on) go away.
 */
public class JavaFXLauncher extends ModuleInstall {

    private static final Logger LOG = Logger.getLogger(JavaFXLauncher.class.getName());

    @Override
    public void validate() {
        // Before anything can ask a versioning system about a file.
        VersioningOptOut.apply();
    }

    @Override
    public void restored() {
        SwingWindowSuppressor.install();

        LOG.info("NetBeans Platform loaded, launching JavaFX...");
        Thread thread = new Thread(() -> Application.launch(JavaFXLaunchApp.class), "nbfx-javafx-launcher");
        thread.setDaemon(true);
        thread.start();
    }
}

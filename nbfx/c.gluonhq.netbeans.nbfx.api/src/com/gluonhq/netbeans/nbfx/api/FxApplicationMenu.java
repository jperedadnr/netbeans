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
package com.gluonhq.netbeans.nbfx.api;

/**
 * The native application menu integration of a platform (currently only macOS, whose application
 * menu holds About / Preferences / Quit). A module enabled only on that platform implements this
 * service; the window resolves it from the Lookup and installs it, so the platform-specific code
 * stays out of the launcher.
 *
 * @since 1.0
 */
public interface FxApplicationMenu {

    /**
     * Registers the platform's application-menu handlers, invoking {@code openPreferences} and
     * {@code quit} (both on the JavaFX Application Thread) when the corresponding menu items are
     * chosen. Called once, while the main window is being built.
     *
     * @param openPreferences opens the Options dialog
     * @param quit            runs the normal exit path (confirming unsaved changes)
     */
    void install(Runnable openPreferences, Runnable quit);
}

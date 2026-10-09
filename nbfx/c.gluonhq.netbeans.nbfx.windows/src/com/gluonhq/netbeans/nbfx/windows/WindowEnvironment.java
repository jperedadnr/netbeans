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
package com.gluonhq.netbeans.nbfx.windows;

import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import java.util.List;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.openide.filesystems.FileObject;

/**
 * The window system's view of the application host: the services the tab panes need but do not own.
 * <p>
 * The window system (tabs, detached windows, docking) is self-contained, but a few behaviours are
 * supplied by the application: the menu/tool bars a detached window gets, revealing a file in the
 * navigator, switching between projects, and where a tab docks back. Rather than pushing these into
 * static fields of the tab pane, the host registers a single {@code WindowEnvironment} in the global
 * {@link org.openide.util.Lookup}; the window system resolves it and calls it.
 * <p>
 * Every method has a safe default, so a host that does not provide an environment still has a
 * working window system.
 *
 * @since 1.0
 */
public interface WindowEnvironment {

    /**
     * Builds the menu and tool bars a detached window shows at its top, scoping active-document
     * commands to {@code activeDocument}. Returns {@code null} when the host provides no bars.
     *
     * @param activeDocument  the detached window's selected document, observed for changes
     * @param scopedCommands  collects the window-scoped commands so the window can dispose them
     * @return the bars, or {@code null}
     */
    default Region createDetachedBars(ObservableValue<EditorDocument> activeDocument,
            List<Command> scopedCommands) {
        return null;
    }

    /**
     * Reveals {@code file} in the navigator view at {@code index} (0 = Projects, 1 = Files).
     *
     * @return {@code true} if a revealer handled the request
     */
    default boolean revealInNavigator(FileObject file, int index) {
        return false;
    }

    /** Selects the next open project. @return {@code true} if project switching is available */
    default boolean selectNextProject() {
        return false;
    }

    /** Selects the previous open project. @return {@code true} if project switching is available */
    default boolean selectPreviousProject() {
        return false;
    }

    /**
     * The pane {@code tab} belongs in by default - the editor pane for editors, the navigator pane
     * or a view's default location for views - used when a tab is docked back from a detached window.
     *
     * @return the home pane, or {@code null} if unknown
     */
    default TabPane homeOf(Tab tab) {
        return null;
    }

    /** Whether a reveal is currently moving focus into the navigator. */
    default boolean isNavigatorRevealInProgress() {
        return false;
    }

    /** Marks a reveal as in progress, or finished. */
    default void setNavigatorRevealInProgress(boolean inProgress) {
    }
}

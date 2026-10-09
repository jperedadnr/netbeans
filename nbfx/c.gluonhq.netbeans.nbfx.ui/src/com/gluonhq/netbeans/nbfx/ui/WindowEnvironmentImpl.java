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
package com.gluonhq.netbeans.nbfx.ui;

import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.ui.actions.ActionBars;
import com.gluonhq.netbeans.nbfx.ui.project.ProjectSwitcher;
import com.gluonhq.netbeans.nbfx.windows.WindowEnvironment;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 * The launcher's {@link WindowEnvironment}: it supplies the window system with the host services -
 * the shared {@link ActionBars}, the navigator revealer, the project switcher and the tab home
 * resolver - which used to be static setters on the tab pane. The launcher configures the single
 * instance (obtained through the Lookup) while starting up.
 */
@ServiceProvider(service = WindowEnvironment.class)
public final class WindowEnvironmentImpl implements WindowEnvironment {

    private ActionBars actionBars;
    private BiConsumer<FileObject, Integer> navigatorRevealer;
    private Function<Tab, TabPane> homeResolver;
    private ProjectSwitcher projectSwitcher;
    private boolean navigatorRevealInProgress;

    /** Sets the bars used to build the menu and tool bars of each detached window. */
    void setActionBars(ActionBars actionBars) {
        this.actionBars = actionBars;
    }

    /** Sets the hook used by detached editor windows to reveal their selected file in the navigator. */
    void setNavigatorRevealer(BiConsumer<FileObject, Integer> navigatorRevealer) {
        this.navigatorRevealer = navigatorRevealer;
    }

    /** Sets how the pane a tab belongs in by default is found. */
    void setHomeResolver(Function<Tab, TabPane> homeResolver) {
        this.homeResolver = homeResolver;
    }

    /** Sets the switcher driving the Next/Previous Project shortcut in every window. */
    void setProjectSwitcher(ProjectSwitcher projectSwitcher) {
        this.projectSwitcher = projectSwitcher;
    }

    @Override
    public Region createDetachedBars(ObservableValue<EditorDocument> activeDocument,
            List<Command> scopedCommands) {
        return actionBars == null ? null : actionBars.createDetachedBars(activeDocument, scopedCommands);
    }

    @Override
    public boolean revealInNavigator(FileObject file, int index) {
        if (navigatorRevealer != null && file != null) {
            navigatorRevealer.accept(file, index);
            return true;
        }
        return false;
    }

    @Override
    public boolean selectNextProject() {
        if (projectSwitcher == null) {
            return false;
        }
        projectSwitcher.next();
        return true;
    }

    @Override
    public boolean selectPreviousProject() {
        if (projectSwitcher == null) {
            return false;
        }
        projectSwitcher.previous();
        return true;
    }

    @Override
    public TabPane homeOf(Tab tab) {
        return homeResolver == null ? null : homeResolver.apply(tab);
    }

    @Override
    public boolean isNavigatorRevealInProgress() {
        return navigatorRevealInProgress;
    }

    @Override
    public void setNavigatorRevealInProgress(boolean inProgress) {
        this.navigatorRevealInProgress = inProgress;
    }
}

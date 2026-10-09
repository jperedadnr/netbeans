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
package com.gluonhq.netbeans.nbfx.output;

import com.gluonhq.netbeans.nbfx.annotations.FxViewLocation;
import com.gluonhq.netbeans.nbfx.annotations.FxViewRegistration;
import com.gluonhq.netbeans.nbfx.api.view.DockLocation;
import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import java.util.List;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Output view: a dockable tab, opening along the bottom of the main area by default, that shows
 * every {@link FxConsole} as an inner tab.
 *
 * @since 1.0
 */
@ServiceProvider(service = ViewProvider.class)
@FxViewRegistration(id = OutputViewProvider.ID, displayName = "Output",
        location = FxViewLocation.CENTER_BOTTOM, position = 30)
public final class OutputViewProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and the view manager. */
    public static final String ID = "output";

    private TabPane pane;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(OutputViewProvider.class, "Output.title");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.CENTER_BOTTOM;
    }

    @Override
    public synchronized Node getView() {
        if (pane == null) {
            pane = new TabPane();
            pane.getStyleClass().add("output-view");
            if (Lookup.getDefault().lookup(FxOutput.class) instanceof FxOutputImpl output) {
                for (ConsoleModel console : List.copyOf(output.consoles())) {
                    addConsoleTab(console);
                }
                output.consoles().addListener((ListChangeListener<ConsoleModel>) change -> {
                    while (change.next()) {
                        for (ConsoleModel console : change.getAddedSubList()) {
                            addConsoleTab(console);
                        }
                    }
                });
            }
        }
        return pane;
    }

    private void addConsoleTab(ConsoleModel console) {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("output-console");
        Tab tab = new Tab(console.getName(), area);
        console.attach(area::appendText, area::clear,
                () -> pane.getSelectionModel().select(tab));
        pane.getTabs().add(tab);
        pane.getSelectionModel().select(tab);
    }

    /** Brings the Output view on screen. Must run on the JavaFX Application Thread. */
    public void show() {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static OutputViewProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(OutputViewProvider.class::isInstance)
                .map(OutputViewProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}

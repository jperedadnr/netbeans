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
package com.gluonhq.netbeans.nbfx.structure;

import com.gluonhq.netbeans.nbfx.api.actions.FxActionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * The Navigator window's content: a header with the panel selector (one entry per
 * {@link FxStructurePanel} that handles the current content type; hidden when there is a single
 * panel, as the Swing {@code NavigatorTC} hides its) and the active panel's
 * {@linkplain FxStructurePanel#getToolbar() toolbar}, above the active panel's component.
 * <p>
 * It follows the global action context: when the active file or editor document changes, it resolves
 * the panels for the new content type, activates the one selected last for that type (or keeps the
 * current one) and tells the panels when they become active or inactive.
 */
final class StructurePanel extends BorderPane {

    /** The preference recording the panel selected last for a content type, by its class name. */
    private static final String LAST_PANEL = "lastPanel.";

    private final ComboBox<FxStructurePanel> selector = new ComboBox<>();
    private final HBox header = new HBox(selector);
    private final StackPane content = new StackPane();
    private final Label empty = new Label(NbBundle.getMessage(StructurePanel.class, "StructurePanel.empty"));
    private final Preferences prefs = NbPreferences.forModule(StructurePanel.class);
    /**
     * The context's file result, kept for as long as the panel lives: a proxy lookup only holds its
     * results weakly, so an unreferenced result is collected with its listener and never fires.
     */
    private Lookup.Result<FileObject> files;
    private FxStructurePanel active;
    private Node activeToolbar;
    private String currentContentType;

    StructurePanel() {
        empty.setDisable(true);
        selector.setConverter(new StringConverter<>() {
            @Override
            public String toString(FxStructurePanel panel) {
                return panel == null ? "" : panel.getDisplayName();
            }

            @Override
            public FxStructurePanel fromString(String string) {
                return null;
            }
        });
        selector.setFocusTraversable(false);
        selector.valueProperty().addListener((observable, old, now) -> activate(now));
        header.getStyleClass().addAll("structure-header", "navigator-header");
        getStylesheets().add(Objects.requireNonNull(StructurePanel.class.getResource("navigator.css")).toExternalForm());
        setTop(header);
        setCenter(content);
        content.getChildren().add(empty);

        FxActionContext context = FxActionContext.getDefault();
        if (context != null) {
            // The global context follows the active component (§22): the file it carries is all that
            // decides the content type, so a single FileObject result is enough.
            files = context.lookupResult(FileObject.class);
            files.addLookupListener(event -> {
                if (Platform.isFxApplicationThread()) {
                    update(context);
                } else {
                    Platform.runLater(() -> update(context));
                }
            });
            update(context);
        }
    }

    /** Re-resolves the panels for the current content type, rebuilding the selector when it changes. */
    private void update(FxActionContext context) {
        String contentType = contentTypeOf(context);
        if (Objects.equals(contentType, currentContentType)) {
            return;
        }
        currentContentType = contentType;
        List<FxStructurePanel> panels = panelsFor(contentType);
        selector.getItems().setAll(panels);
        boolean hasPanels = !panels.isEmpty();
        // A single panel needs no selector: the header then only shows the panel's toolbar, if any.
        selector.setVisible(panels.size() > 1);
        selector.setManaged(panels.size() > 1);
        if (!hasPanels) {
            activate(null);
        } else if (active != null && panels.contains(active)) {
            selector.setValue(active);
        } else {
            selector.setValue(lastPanel(contentType, panels));
        }
        updateHeader();
    }

    /** Shows the header while it has something to show: the selector of several panels, or the active panel's toolbar. */
    private void updateHeader() {
        boolean showHeader = active != null && (selector.isVisible() || activeToolbar != null);
        header.setVisible(showHeader);
        header.setManaged(showHeader);
    }

    /** Selects {@code panel} when it is one of the current content type's; see {@link StructureView#select}. */
    void select(FxStructurePanel panel) {
        if (selector.getItems().contains(panel)) {
            selector.setValue(panel);
        }
    }

    @Override
    public void requestFocus() {
        if (active != null) {
            active.getComponent().requestFocus();
        } else {
            super.requestFocus();
        }
    }

    private void activate(FxStructurePanel panel) {
        if (panel == active) {
            return;
        }
        if (active != null) {
            active.panelDeactivated();
        }
        active = panel;
        if (activeToolbar != null) {
            header.getChildren().remove(activeToolbar);
            activeToolbar = null;
        }
        if (panel == null) {
            content.getChildren().setAll(empty);
            updateHeader();
            return;
        }
        content.getChildren().setAll(panel.getComponent());
        // The panel's toolbar shares the header row with the selector, which otherwise takes it all.
        activeToolbar = panel.getToolbar();
        if (activeToolbar != null) {
            HBox.setHgrow(activeToolbar, Priority.ALWAYS);
            header.getChildren().add(activeToolbar);
        }
        HBox.setHgrow(selector, activeToolbar == null ? Priority.ALWAYS : Priority.NEVER);
        selector.setMaxWidth(activeToolbar == null ? Double.MAX_VALUE : Region.USE_COMPUTED_SIZE);
        updateHeader();
        if (currentContentType != null) {
            prefs.put(LAST_PANEL + currentContentType, panel.getClass().getName());
        }
        FxActionContext context = FxActionContext.getDefault();
        if (context != null) {
            panel.panelActivated(context);
        }
    }

    /** The panel of {@code panels} selected last for {@code contentType}, else the first. */
    private FxStructurePanel lastPanel(String contentType, List<FxStructurePanel> panels) {
        String last = prefs.get(LAST_PANEL + contentType, null);
        for (FxStructurePanel panel : panels) {
            if (panel.getClass().getName().equals(last)) {
                return panel;
            }
        }
        return panels.getFirst();
    }

    /** The content type to show the structure for, or {@code null} when there is no file. */
    private static String contentTypeOf(FxActionContext context) {
        FileObject file = FxStructurePanel.selectedFile(context);
        return file == null ? null : file.getMIMEType();
    }

    private static List<FxStructurePanel> panelsFor(String contentType) {
        if (contentType == null) {
            return List.of();
        }
        List<FxStructurePanel> panels = new ArrayList<>();
        for (FxStructurePanel panel : Lookup.getDefault().lookupAll(FxStructurePanel.class)) {
            if (panel.handles(contentType)) {
                panels.add(panel);
            }
        }
        return panels;
    }
}

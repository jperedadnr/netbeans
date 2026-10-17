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
package com.gluonhq.netbeans.nbfx.structure.java.ui;

import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.structure.BackgroundScans;
import com.gluonhq.netbeans.nbfx.structure.DocumentTracker;
import com.gluonhq.netbeans.nbfx.structure.FxStructurePanel;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import com.gluonhq.netbeans.nbfx.structure.StructureView;
import com.gluonhq.netbeans.nbfx.structure.java.model.InspectedType;
import com.gluonhq.netbeans.nbfx.structure.java.model.MemberFilters;
import com.gluonhq.netbeans.nbfx.structure.java.model.MembersScanner;
import com.gluonhq.netbeans.nbfx.structure.java.model.TypeResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

import static com.gluonhq.netbeans.nbfx.structure.java.ui.ClassMemberPanelUI.message;

/**
 * The Members panel of the Navigator window, after NetBeans' {@code ClassMemberPanel}: the types
 * and members of the Java file in the global action context, with the filter bar and, in the
 * window's header, the Inspect Members history and the Javadoc button. Navigate &#9656; Inspect
 * Members and Inspect File Members show a type here aside from the context ({@link #inspectAt},
 * {@link #inspectFile}), recording it in the history.
 */
@ServiceProvider(service = FxStructurePanel.class, position = 100)
public final class ClassMemberPanel extends JavaStructurePanel<MembersScanner.Result> {

    private static final Logger LOG = Logger.getLogger(ClassMemberPanel.class.getName());
    /** The history entry standing for the automatic mode. */
    private static final Object AUTO = new Object();

    private final MemberFilters filters = new MemberFilters();
    private final NavigatorSettings settings = new NavigatorSettings();
    private final InspectHistory inspectHistory = new InspectHistory();
    /** The Inspect Members history: {@link #AUTO} - following the context - and the inspected types. */
    private ComboBox<Object> history;
    private Button javadoc;
    private HBox toolbar;
    /** Set while the history combo is updated from the tracker, so its selection fires no inspection. */
    private boolean syncingHistory;

    @Override
    public String getDisplayName() {
        return message("LBL_Members");
    }

    @Override
    protected ScanningPanel<MembersScanner.Result> createUI() {
        settings.load(filters);
        settings.watch(filters);
        return new ClassMemberPanelUI(filters);
    }

    @Override
    protected void trackerCreated(DocumentTracker<MembersScanner.Result> tracker) {
        tracker.pinnedProperty().subscribe(key -> syncHistory((InspectedType) key));
    }

    /**
     * The Inspect Members history - disabled until a type has been inspected - and the button
     * opening the Javadoc window, a placeholder of the NetBeans feature still to come.
     */
    @Override
    public Node getToolbar() {
        if (toolbar == null) {
            history = new ComboBox<>();
            history.setCellFactory(list -> new HistoryCell());
            history.setButtonCell(new HistoryCell());
            history.setTooltip(new Tooltip(message("TOOLTIP_InspectMembersHistory")));
            history.setMaxWidth(Double.MAX_VALUE);
            history.setFocusTraversable(false);
            HBox.setHgrow(history, Priority.ALWAYS);
            history.getSelectionModel().selectedItemProperty().subscribe(entry -> {
                if (syncingHistory || entry == null) {
                    return;
                }
                if (entry instanceof InspectedType type) {
                    tracker().pin(type.file(), type);
                } else {
                    tracker().unpin();
                }
            });
            javadoc = new Button();
            ClassMemberPanelUI.decorate(javadoc, NavigatorIcons.view("javadoc_open"), "TOOLTIP_OpenJDoc");
            javadoc.setDisable(true);
            toolbar = new HBox(history, javadoc);
            toolbar.getStyleClass().addAll("navigator-view", "navigator-header");
            ui();
            syncHistory(null);
        }
        return toolbar;
    }

    /** Shows the file declaring {@code type}, its row selected, and records it in the history. FX thread. */
    public void inspect(InspectedType type) {
        inspectHistory.add(type);
        tracker().pin(type.file(), type);
    }

    /**
     * Inspects the members of the type at {@code offset} of {@code file} (Navigate &#9656; Inspect
     * Members): resolves it in the background, then shows the Navigator window with this panel and
     * the file declaring the type, its row selected, and records it in the history. Reports when
     * nothing typed is there. FX thread.
     */
    public void inspectAt(FileObject file, int offset) {
        inspect(file, text -> TypeResolver.atCaret(file, text, offset));
    }

    /** Inspects the members of the main type of {@code file} (Navigate &#9656; Inspect File Members). FX thread. */
    public void inspectFile(FileObject file) {
        inspect(file, text -> TypeResolver.mainType(file, text));
    }

    private void inspect(FileObject file, Resolution resolution) {
        OpenSources openSources = Lookup.getDefault().lookup(OpenSources.class);
        String text = openSources == null ? null : openSources.textOf(file);
        BackgroundScans.EXECUTOR.execute(() -> {
            InspectedType type = null;
            Throwable failure = null;
            try {
                String source = text != null ? text : file.asText().replace("\r\n", "\n").replace('\r', '\n');
                type = resolution.resolve(source);
            } catch (IOException | RuntimeException ex) {
                failure = ex;
            }
            InspectedType resolved = type;
            Throwable failed = failure;
            Platform.runLater(() -> {
                String title = message("TITLE_InspectMembers");
                if (failed != null) {
                    LOG.log(Level.WARNING, "Could not inspect " + file.getPath(), failed);
                    ErrorReporter.report(title, null, message("ERR_Inspect"), failed);
                    return;
                }
                if (resolved == null) {
                    ErrorReporter.report(title, null, message("ERR_NotDeclaredType"));
                    return;
                }
                StructureView view = StructureView.instance();
                if (view != null) {
                    view.show();
                    view.select(this);
                }
                inspect(resolved);
            });
        });
    }

    /** Resolves the type to inspect from a file's text; background thread. */
    private interface Resolution {
        InspectedType resolve(String text) throws IOException;
    }

    /**
     * Lists the history ({@code <auto>} first) and selects the entry of the type inspected, or
     * {@code <auto>}. The items are only replaced when the history changed: this also runs while
     * the combo's own list delivers the selection the user just made, and replacing the items under
     * that delivery breaks it.
     */
    private void syncHistory(InspectedType inspected) {
        if (history == null) {
            return;
        }
        List<Object> items = new ArrayList<>();
        if (inspectHistory.isEmpty()) {
            items.add(message("TXT_InspectMembersHistoryEmpty"));
        } else {
            items.add(AUTO);
            items.addAll(inspectHistory.entries());
        }
        syncingHistory = true;
        try {
            if (!history.getItems().equals(items)) {
                history.getItems().setAll(items);
            }
            history.setDisable(inspectHistory.isEmpty());
            Object selected = inspectHistory.isEmpty() ? items.get(0) : inspected == null ? AUTO : inspected;
            if (history.getSelectionModel().getSelectedItem() != selected) {
                history.getSelectionModel().select(selected);
            }
        } finally {
            syncingHistory = false;
        }
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static ClassMemberPanel instance() {
        return Lookup.getDefault().lookupAll(FxStructurePanel.class).stream()
                .filter(ClassMemberPanel.class::isInstance)
                .map(ClassMemberPanel.class::cast)
                .findFirst()
                .orElse(null);
    }

    /** A history entry: the type's simple name, its qualified name as tooltip; {@code <auto>} / {@code <empty>} as they are. */
    private static final class HistoryCell extends ListCell<Object> {

        @Override
        protected void updateItem(Object entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setTooltip(null);
            } else if (entry instanceof InspectedType type) {
                setText(type.getSimpleName());
                setTooltip(new Tooltip(type.getQualifiedName()));
            } else {
                setText(entry == AUTO ? message("TXT_InspectMembersHistoryAuto") : entry.toString());
                setTooltip(null);
            }
        }
    }
}

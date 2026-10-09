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
package com.gluonhq.netbeans.nbfx.javanavigator.ui;

import com.gluonhq.netbeans.nbfx.javanavigator.model.InspectedType;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberFilters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import static com.gluonhq.netbeans.nbfx.javanavigator.ui.MembersPanel.message;

/**
 * The content of the Navigator tab, after NetBeans' Navigator window for Java files: a header row
 * with the view selector (Members, Bean Patterns), the Inspect Members history and the Javadoc
 * window button, then the selected view - a {@link MembersPanel} or a {@link BeanPatternsPanel} -
 * or a placeholder when the active editor shows a file the Navigator has no view for.
 */
final class NavigatorView extends BorderPane {

    /** The views of a Java file, in selector order. */
    enum View {
        MEMBERS("LBL_Members"),
        BEAN_PATTERNS("LBL_BeanPatterns");

        private final String labelKey;

        View(String labelKey) {
            this.labelKey = labelKey;
        }

        String label() {
            return message(labelKey);
        }
    }

    private final MemberFilters filters = new MemberFilters();
    private final NavigatorSettings settings;
    private final MembersPanel members;
    private final BeanPatternsPanel beanPatterns;
    private final ComboBox<View> views = new ComboBox<>();
    /** The Inspect Members history: {@link #AUTO} - following the editor - and the inspected types. */
    private final ComboBox<Object> history = new ComboBox<>();
    private final InspectHistory inspectHistory = new InspectHistory();
    private final Button javadoc = new Button();
    /** Set while the history combo is updated from the tracker, so its selection fires no inspection. */
    private boolean syncingHistory;

    /** The history entry standing for the automatic mode. */
    private static final Object AUTO = new Object();
    private final Label unavailable = new Label(message("LBL_NoViewAvailable"));
    private final DocumentTracker tracker;
    private NavigatorPanel<?> panel;

    NavigatorView() {
        this(new NavigatorSettings());
    }

    NavigatorView(NavigatorSettings settings) {
        this.settings = settings;
        settings.load(filters);
        settings.watch(filters);
        getStyleClass().add("navigator-view");
        getStylesheets().add(Objects.requireNonNull(NavigatorView.class.getResource("navigator.css")).toExternalForm());

        members = new MembersPanel(filters);
        beanPatterns = new BeanPatternsPanel();
        panel = panelOf(settings.getView());
        unavailable.getStyleClass().add("navigator-empty");
        StackPane.setAlignment(unavailable, Pos.CENTER);
        StackPane content = new StackPane(members, beanPatterns, unavailable);
        content.setMinHeight(0);
        setTop(createHeader());
        setCenter(content);
        setMinHeight(0);
        showUnavailable();

        tracker = new DocumentTracker(this, panel);
        // Scans are suspended while the tab is closed; they resume when it is shown again.
        sceneProperty().subscribe(scene -> tracker.setActive(scene != null));
        views.getSelectionModel().selectedItemProperty().subscribe(view -> {
            if (view != null) {
                select(view);
            }
        });
        history.getSelectionModel().selectedItemProperty().subscribe(entry -> {
            if (syncingHistory || entry == null) {
                return;
            }
            if (entry instanceof InspectedType type) {
                tracker.inspect(type);
            } else {
                tracker.returnToAuto();
            }
        });
        tracker.inspectedProperty().subscribe(this::syncHistory);
        tracker.start();
    }

    /** Shows the file declaring {@code type}, its row selected, and records it in the history. Must run on the FX thread. */
    void inspect(InspectedType type) {
        inspectHistory.add(type);
        tracker.inspect(type);
    }

    /**
     * Lists the history ({@code <auto>} first) and selects the entry of the type inspected, or
     * {@code <auto>}. The items are only replaced when the history changed: this also runs while
     * the combo's own list delivers the selection the user just made, and replacing the items under
     * that delivery breaks it.
     */
    private void syncHistory(InspectedType inspected) {
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

    /** Shows the selected view's panel, hiding the placeholder. */
    void showContent() {
        members.setVisible(panel == members);
        beanPatterns.setVisible(panel == beanPatterns);
        unavailable.setVisible(false);
    }

    /** Shows the placeholder: the active editor holds no file the Navigator has a view for. */
    void showUnavailable() {
        members.clear();
        beanPatterns.clear();
        members.setVisible(false);
        beanPatterns.setVisible(false);
        unavailable.setVisible(true);
    }

    @Override
    public void requestFocus() {
        panel.getNode().requestFocus();
    }

    private NavigatorPanel<?> panelOf(View view) {
        return view == View.BEAN_PATTERNS ? beanPatterns : members;
    }

    /**
     * Switches to {@code view}: its panel takes over and the file is scanned for it. The history and
     * the Javadoc button belong to the Members view, as in NetBeans; the selector takes the whole row
     * otherwise.
     */
    private void select(View view) {
        settings.setView(view);
        panel = panelOf(view);
        boolean membersView = view == View.MEMBERS;
        history.setVisible(membersView);
        history.setManaged(membersView);
        javadoc.setVisible(membersView);
        javadoc.setManaged(membersView);
        HBox.setHgrow(views, membersView ? Priority.NEVER : Priority.ALWAYS);
        if (tracker != null) {
            tracker.setPanel(panel);
        }
        if (!unavailable.isVisible()) {
            showContent();
        }
    }

    /**
     * The header: the view selector, the Inspect Members history - disabled until a type has been
     * inspected, and only for the Members view - and the button opening the Javadoc window, both
     * placeholders of the NetBeans features still to come.
     */
    private HBox createHeader() {
        views.getItems().addAll(View.values());
        views.setCellFactory(list -> new ViewCell());
        views.setButtonCell(new ViewCell());
        views.getSelectionModel().select(settings.getView());
        views.setTooltip(new Tooltip(message("TOOLTIP_SelectView")));
        views.setFocusTraversable(false);
        views.setMaxWidth(Double.MAX_VALUE);

        history.setCellFactory(list -> new HistoryCell());
        history.setButtonCell(new HistoryCell());
        history.setTooltip(new Tooltip(message("TOOLTIP_InspectMembersHistory")));
        syncHistory(null);
        history.setMaxWidth(Double.MAX_VALUE);
        history.setFocusTraversable(false);
        HBox.setHgrow(history, Priority.ALWAYS);

        MembersPanel.decorate(javadoc, NavigatorIcons.view("javadoc_open"), "TOOLTIP_OpenJDoc");
        javadoc.setDisable(true);

        HBox header = new HBox(views, history, javadoc);
        header.getStyleClass().add("navigator-header");
        // select(View) sets what the row shows for the selected view once the combo is wired.
        return header;
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

    private static final class ViewCell extends ListCell<View> {

        @Override
        protected void updateItem(View view, boolean empty) {
            super.updateItem(view, empty);
            setText(empty || view == null ? null : view.label());
        }
    }
}

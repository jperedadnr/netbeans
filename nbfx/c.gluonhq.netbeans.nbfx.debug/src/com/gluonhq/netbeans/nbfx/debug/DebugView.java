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
package com.gluonhq.netbeans.nbfx.debug;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import org.netbeans.api.debugger.jpda.CallStackFrame;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.openide.util.NbBundle;

/**
 * The Debug view: the current session's threads, call stack and variables, with Continue / Step
 * Over / Step Into / Step Out / Stop. It binds to the current {@link DebugSession} and follows it as
 * sessions start and stop.
 *
 * @since 1.0
 */
final class DebugView extends BorderPane {

    private final ListView<JPDAThread> threads = new ListView<>();
    private final TableView<CallStackFrame> frames = new TableView<>();
    private final TableView<VariableRow> variables = new TableView<>();
    private final Label status = new Label();

    private DebugSession session;

    DebugView() {
        getStyleClass().add("debug-view");

        threads.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(JPDAThread item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        TableColumn<CallStackFrame, String> frameClass = new TableColumn<>(message("Debug.column.name"));
        frameClass.setCellValueFactory(cell -> new SimpleStringProperty(
                safe(() -> cell.getValue().getClassName())));
        TableColumn<CallStackFrame, String> frameMethod = new TableColumn<>(message("Debug.frames"));
        frameMethod.setCellValueFactory(cell -> new SimpleStringProperty(
                safe(() -> cell.getValue().getMethodName())));
        frames.getColumns().add(frameClass);
        frames.getColumns().add(frameMethod);
        frames.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<VariableRow, String> name = new TableColumn<>(message("Debug.column.name"));
        name.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        TableColumn<VariableRow, String> type = new TableColumn<>(message("Debug.column.type"));
        type.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        TableColumn<VariableRow, String> value = new TableColumn<>(message("Debug.column.value"));
        value.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().value()));
        variables.getColumns().add(name);
        variables.getColumns().add(type);
        variables.getColumns().add(value);
        variables.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button continueButton = button("Debug.continue", DebugSession::resume);
        Button stepOver = button("Debug.stepOver", DebugSession::stepOver);
        Button stepInto = button("Debug.stepInto", DebugSession::stepInto);
        Button stepOut = button("Debug.stepOut", DebugSession::stepOut);
        Button stop = button("Debug.stop", DebugSession::stop);
        var inactive = DebugStatus.ACTIVE.not();
        for (Button button : new Button[] {continueButton, stepOver, stepInto, stepOut, stop}) {
            button.disableProperty().bind(inactive);
        }
        setTop(new ToolBar(continueButton, stepOver, stepInto, stepOut, stop, new Separator(), status));

        SplitPane top = new SplitPane(threads, frames);
        top.setDividerPositions(0.35);
        SplitPane all = new SplitPane(top, variables);
        all.setOrientation(Orientation.VERTICAL);
        all.setDividerPositions(0.6);
        setCenter(all);

        DebugStatus.ACTIVE.addListener((observable, was, now) -> bind());
        bind();
    }

    /** Re-binds to the current session, following sessions as they start and stop. */
    private void bind() {
        session = DebugSession.current();
        status.textProperty().unbind();
        if (session == null) {
            threads.setItems(FXCollections.observableArrayList());
            frames.setItems(FXCollections.observableArrayList());
            variables.setItems(FXCollections.observableArrayList());
            status.setText("");
            return;
        }
        threads.setItems(session.threads());
        frames.setItems(session.frames());
        variables.setItems(session.variables());
        status.textProperty().bind(session.statusProperty());
        session.selectedThreadProperty().addListener((observable, old, now) ->
                threads.getSelectionModel().select(now));
        threads.getSelectionModel().selectedItemProperty().addListener((observable, old, now) -> {
            if (now != null) {
                session.selectedThreadProperty().set(now);
            }
        });
        session.selectedFrameProperty().addListener((observable, old, now) ->
                frames.getSelectionModel().select(now));
        frames.getSelectionModel().selectedItemProperty().addListener((observable, old, now) -> {
            if (now != null) {
                session.selectedFrameProperty().set(now);
            }
        });
    }

    private static Button button(String key, Consumer<DebugSession> action) {
        Button button = new Button(message(key));
        button.setFocusTraversable(false);
        button.setOnAction(event -> {
            DebugSession current = DebugSession.current();
            if (current != null) {
                action.accept(current);
            }
        });
        return button;
    }

    private static String safe(Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(DebugView.class, key);
    }
}

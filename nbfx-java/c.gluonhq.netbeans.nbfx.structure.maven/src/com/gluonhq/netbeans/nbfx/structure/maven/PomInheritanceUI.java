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
package com.gluonhq.netbeans.nbfx.structure.maven;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.structure.NavigatorIcons;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import java.io.File;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The POM inheritance panel, after NetBeans' {@code POMInheritancePanel}: the POM and its parents,
 * one row each as <em>artifactId - version</em>, a parent from the local repository marked
 * read-only. Double-clicking a row (or Enter) opens that POM.
 */
final class PomInheritanceUI extends BorderPane implements ScanningPanel<PomInheritanceUI.Result> {

    /** The scan of a POM: its lineage; {@code null} layers while the text is not a well-formed POM. */
    record Result(FileObject file, List<PomLayer> layers, String problem) {
    }

    private final ListView<PomLayer> list = new ListView<>();
    private final Label problem = new Label();
    private Result result;
    private FileObject shownFile;

    PomInheritanceUI() {
        list.setMinHeight(0);
        list.getStyleClass().add("navigator-tree");
        list.setCellFactory(view -> new LayerCell());
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                openSelected();
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelected();
                e.consume();
            }
        });
        problem.getStyleClass().add("navigator-empty");
        problem.setVisible(false);
        problem.setManaged(false);
        setTop(problem);
        setCenter(list);
        setMinHeight(0);
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Result scan(FileObject file, String text, Cancellation cancellation) {
        File pomFile = FileUtil.toFile(file);
        if (pomFile == null) {
            return null;
        }
        PomLineage.Result lineage = PomLineage.read(pomFile, text);
        return new Result(file, lineage.layers(), lineage.problem());
    }

    @Override
    public void show(Result newResult) {
        if (newResult == null) {
            clear();
            return;
        }
        boolean sameFile = newResult.file().equals(shownFile);
        if (newResult.layers() == null) {
            if (!sameFile) {
                clear();
            }
            return;
        }
        result = newResult;
        shownFile = newResult.file();
        list.getItems().setAll(newResult.layers());
        problem.setText(newResult.problem());
        problem.setVisible(newResult.problem() != null);
        problem.setManaged(newResult.problem() != null);
    }

    @Override
    public void clear() {
        result = null;
        shownFile = null;
        list.getItems().clear();
        problem.setVisible(false);
        problem.setManaged(false);
    }

    @Override
    public void selectAt(int offset) {
        // The rows are whole POMs: the caret selects none.
    }

    private void openSelected() {
        PomLayer layer = list.getSelectionModel().getSelectedItem();
        if (layer == null || result == null) {
            return;
        }
        FileObject file = layer.current() ? result.file() : layer.file() == null ? null : FileUtil.toFileObject(layer.file());
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (file != null && contentManager != null) {
            contentManager.openFile(file, 0, 0);
            contentManager.focusActiveEditor();
        }
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(PomInheritanceUI.class, key, args);
    }

    /** A row: the Maven icon, {@code artifactId - version}, and <em>(read-only)</em> for a repository POM; the nodes are reused. */
    private static final class LayerCell extends ListCell<PomLayer> {

        private final Label title = new Label();
        private final Label readOnly = new Label();
        private final HBox row;
        private final Tooltip tooltip = new Tooltip();

        LayerCell() {
            getStyleClass().add("navigator-cell");
            title.getStyleClass().add("navigator-text");
            readOnly.getStyleClass().add("navigator-detail");
            readOnly.setStyle("-fx-font-style: italic;");
            HBox label = new HBox(title, readOnly);
            label.setAlignment(Pos.BASELINE_LEFT);
            row = new HBox(4, NavigatorIcons.view(PomInheritanceUI.class, "icons/maven.png"), label);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(PomLayer layer, boolean empty) {
            super.updateItem(layer, empty);
            if (empty || layer == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            title.setText(message("TITLE_PomNode", layer.artifactId(), String.valueOf(layer.version())));
            readOnly.setText(layer.readOnly() ? " " + message("TITLE_ReadOnly") : "");
            setGraphic(row);
            if (layer.file() == null) {
                setTooltip(null);
            } else {
                tooltip.setText(layer.file().getAbsolutePath());
                setTooltip(tooltip);
            }
        }
    }
}

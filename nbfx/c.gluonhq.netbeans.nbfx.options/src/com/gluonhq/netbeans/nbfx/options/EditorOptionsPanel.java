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
package com.gluonhq.netbeans.nbfx.options;

import com.gluonhq.netbeans.nbfx.annotations.FxOptionsRegistration;
import com.gluonhq.netbeans.nbfx.api.editor.EditorSettings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.GridPane;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The Editor options panel: whether every editor shows line numbers and the breadcrumbs bar. The
 * check boxes are bound to the shared {@link EditorSettings}, which the launcher persists with the
 * session.
 *
 * @since 1.0
 */
@FxOptionsRegistration(id = EditorOptionsPanel.ID, categoryName = "Editor", position = 10)
public final class EditorOptionsPanel implements FxOptionsPanel {

    /** The panel id, referenced by {@code @FxOptionsRegistration}. */
    public static final String ID = "editor";

    private final GridPane grid = new GridPane();

    /** Creates the panel. Must run on the JavaFX Application Thread. */
    public EditorOptionsPanel() {
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(8));
        EditorSettings settings = Lookup.getDefault().lookup(EditorSettings.class);
        if (settings != null) {
            CheckBox lineNumbers = new CheckBox(message("Options.showLineNumbers"));
            lineNumbers.selectedProperty().bindBidirectional(settings.showLineNumbers());
            CheckBox breadcrumbs = new CheckBox(message("Options.showBreadcrumbs"));
            breadcrumbs.selectedProperty().bindBidirectional(settings.showBreadcrumbs());
            grid.addRow(0, lineNumbers);
            grid.addRow(1, breadcrumbs);
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return message("Options.category.editor");
    }

    @Override
    public String getCategory() {
        return "Editor";
    }

    @Override
    public int getPosition() {
        return 10;
    }

    @Override
    public Node getComponent() {
        return grid;
    }

    private static String message(String key) {
        return NbBundle.getMessage(EditorOptionsPanel.class, key);
    }
}

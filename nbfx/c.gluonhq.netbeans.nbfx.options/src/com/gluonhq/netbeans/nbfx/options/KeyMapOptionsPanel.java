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
import com.gluonhq.netbeans.nbfx.api.actions.ActionRegistry;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.KeyMap;
import java.util.HashMap;
import java.util.Map;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCombination;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The Keymap options panel: every registered {@link Command} with its shortcut, editable as a
 * {@link KeyCombination} string (e.g. {@code Shortcut+S}). Changes are stored in the {@link KeyMap}.
 *
 * @since 1.0
 */
@FxOptionsRegistration(id = KeyMapOptionsPanel.ID, categoryName = "Keymap", position = 20)
public final class KeyMapOptionsPanel implements FxOptionsPanel {

    /** The panel id, referenced by {@code @FxOptionsRegistration}. */
    public static final String ID = "keymap";

    private final TableView<Command> table = new TableView<>();
    private final Map<String, String> edits = new HashMap<>();
    private final KeyMap keyMap = Lookup.getDefault().lookup(KeyMap.class);

    /** Creates the panel. Must run on the JavaFX Application Thread. */
    public KeyMapOptionsPanel() {
        ActionRegistry registry = Lookup.getDefault().lookup(ActionRegistry.class);
        if (registry != null) {
            table.getItems().setAll(registry.getCommands());
        }

        TableColumn<Command, String> commandColumn = new TableColumn<>(message("Options.keymap.command"));
        commandColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getText()));
        commandColumn.setPrefWidth(260);

        TableColumn<Command, String> shortcutColumn = new TableColumn<>(message("Options.keymap.shortcut"));
        shortcutColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(display(cell.getValue())));
        shortcutColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        shortcutColumn.setOnEditCommit(event -> {
            edits.put(event.getRowValue().getId(), event.getNewValue() == null ? "" : event.getNewValue().trim());
            table.refresh();
        });
        shortcutColumn.setPrefWidth(160);

        table.getColumns().add(commandColumn);
        table.getColumns().add(shortcutColumn);
        table.setEditable(true);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return message("Options.category.keymap");
    }

    @Override
    public String getCategory() {
        return "Keymap";
    }

    @Override
    public int getPosition() {
        return 20;
    }

    @Override
    public Node getComponent() {
        return table;
    }

    @Override
    public void load() {
        edits.clear();
        table.refresh();
    }

    @Override
    public void apply() {
        if (keyMap == null) {
            return;
        }
        for (Map.Entry<String, String> edit : edits.entrySet()) {
            String text = edit.getValue();
            if (text == null || text.isBlank()) {
                keyMap.reset(edit.getKey());
            } else {
                try {
                    keyMap.setAccelerator(edit.getKey(), KeyCombination.keyCombination(text));
                } catch (RuntimeException ex) {
                    // ignore an unparsable combination
                }
            }
        }
        edits.clear();
    }

    @Override
    public void cancel() {
        edits.clear();
        table.refresh();
    }

    private String display(Command command) {
        if (edits.containsKey(command.getId())) {
            return edits.get(command.getId());
        }
        KeyCombination accelerator = keyMap == null
                ? command.getAccelerator()
                : keyMap.accelerator(command.getId(), command.getAccelerator());
        return accelerator == null ? "" : accelerator.getName();
    }

    private static String message(String key) {
        return NbBundle.getMessage(KeyMapOptionsPanel.class, key);
    }
}

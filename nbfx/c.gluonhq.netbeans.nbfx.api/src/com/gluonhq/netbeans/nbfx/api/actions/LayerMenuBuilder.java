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
package com.gluonhq.netbeans.nbfx.api.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Builds JavaFX menu items from the action references contributed to a menu surface, resolving each
 * reference against the {@link ActionRegistry}. This is the JavaFX counterpart of the NetBeans
 * action presenters: the menu contents are declared in the layer, not hard-coded in the UI.
 *
 * @since 1.0
 */
public final class LayerMenuBuilder {

    private static final Logger LOG = Logger.getLogger(LayerMenuBuilder.class.getName());

    private final ActionRegistry registry;

    /**
     * Creates a builder resolving references against {@code registry}.
     *
     * @param registry the action registry that supplies the referenced actions
     */
    public LayerMenuBuilder(ActionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Builds the menu items for {@code folder}, inserting separators where a reference asks for one.
     * Must be called on the JavaFX Application Thread.
     *
     * @param folder the surface folder (for example {@code NbFx/Menus/File}); may be {@code null}
     * @return the menu items, in order
     */
    public List<MenuItem> build(FileObject folder) {
        List<MenuItem> items = new ArrayList<>();
        for (FxActionRef ref : ActionLayerReader.read(folder)) {
            if (ref.separatorBefore() && !items.isEmpty()
                    && !(items.get(items.size() - 1) instanceof SeparatorMenuItem)) {
                items.add(new SeparatorMenuItem());
            }
            registry.find(ref.actionId()).ifPresentOrElse(
                    command -> items.add(item(command)),
                    () -> LOG.warning("No command registered for action reference: " + ref.actionId()));
        }
        return items;
    }

    /**
     * Builds the menu items for the layer folder at {@code layerPath}.
     *
     * @param layerPath the layer path (for example {@code "NbFx/Menus/File"})
     * @return the menu items, in order
     */
    public List<MenuItem> build(String layerPath) {
        return build(layerPath == null ? null : org.openide.filesystems.FileUtil.getConfigFile(layerPath));
    }

    private MenuItem item(Command command) {
        MenuItem item = new MenuItem(command.getText());
        KeyCombination accelerator = accelerator(command);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.disableProperty().bind(command.disabledProperty());
        item.setOnAction(_ -> command.run());
        return item;
    }

    /** The command's accelerator, overridden by the user {@link KeyMap} when one is registered. */
    private static KeyCombination accelerator(Command command) {
        KeyMap keyMap = Lookup.getDefault().lookup(KeyMap.class);
        return keyMap == null ? command.getAccelerator()
                : keyMap.accelerator(command.getId(), command.getAccelerator());
    }
}

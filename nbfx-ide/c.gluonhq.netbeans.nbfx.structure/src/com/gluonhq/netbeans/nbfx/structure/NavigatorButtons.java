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

import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;

/**
 * The flat icon buttons of the Navigator panels and the bar along their bottom that holds the filter
 * toggles (the Swing {@code TapPanel}), styled by the window's stylesheet.
 *
 * @since 1.0
 */
public final class NavigatorButtons {

    private NavigatorButtons() {
    }

    /** A flat toggle showing {@code icon}, with {@code tooltip}; not focus traversable. */
    public static ToggleButton toggle(Node icon, String tooltip) {
        ToggleButton button = new ToggleButton();
        decorate(button, icon, tooltip);
        return button;
    }

    /** Makes {@code button} a flat icon button showing {@code icon}, with {@code tooltip}. */
    public static void decorate(ButtonBase button, Node icon, String tooltip) {
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("navigator-tool-button");
        button.setFocusTraversable(false);
    }

    /** The bar along a panel's bottom holding {@code items}, overflowing into a menu when narrow. */
    public static ToolBar filterBar(Node... items) {
        ToolBar bar = new ToolBar(items);
        bar.getStyleClass().add("navigator-filters");
        return bar;
    }
}

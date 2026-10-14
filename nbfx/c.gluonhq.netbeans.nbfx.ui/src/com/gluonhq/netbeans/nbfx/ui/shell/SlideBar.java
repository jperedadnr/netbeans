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
package com.gluonhq.netbeans.nbfx.ui.shell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * One sliding side of a {@link SlidingArea}: the bar along the left, right, bottom or top edge of the
 * window holding the minimized panes as groups of buttons - a <em>Restore Window Group</em> button
 * heading each group, then a button per tab, with the tab's icon and title (the title written along
 * the bar on the vertical sides, as NetBeans does). The bar hides while it holds no tab. The buttons
 * report the mouse resting on them and clicks to the sliding area, which does the sliding.
 */
final class SlideBar extends StackPane {

    private static final PseudoClass SLID = PseudoClass.getPseudoClass("slid");
    private static final PseudoClass FOLLOWS = PseudoClass.getPseudoClass("follows");
    private static final double SPACING = 2;

    /** The buttons of one minimized pane. */
    private final class PaneGroup {

        final TabPane pane;
        final Pane box = newBox();
        final Map<Tab, Button> buttons = new LinkedHashMap<>();
        final ListChangeListener<Tab> tabsListener = _ -> rebuild();

        PaneGroup(TabPane pane) {
            this.pane = pane;
            box.getStyleClass().add("nbfx-slide-group");
            pane.getTabs().addListener(tabsListener);
            rebuild();
        }

        void rebuild() {
            buttons.clear();
            List<Node> children = new ArrayList<>();
            Button restore = SlidingArea.iconButton("restore", SlidingArea.message("SlideBar.restoreGroup.tooltip"));
            restore.setOnAction(_ -> owner.restore(pane));
            children.add(restore);
            // The group's tabs include the one on loan to the slid-in panel.
            List<Tab> tabs = owner.tabsOf(pane);
            for (Tab tab : tabs) {
                Button button = button(tab);
                buttons.put(tab, button);
                children.add(button);
            }
            box.getChildren().setAll(children);
            box.setVisible(!tabs.isEmpty());
            box.setManaged(!tabs.isEmpty());
            refreshVisibility();
            if (slid != null && buttons.containsKey(slid)) {
                buttons.get(slid).pseudoClassStateChanged(SLID, true);
            }
        }

        void dispose() {
            pane.getTabs().removeListener(tabsListener);
        }
    }

    private final Side side;
    private final SlidingArea owner;
    private final Pane strip = newBox();
    private final Map<TabPane, PaneGroup> groups = new LinkedHashMap<>();
    private Tab slid;

    SlideBar(Side side, SlidingArea owner) {
        this.side = side;
        this.owner = owner;
        getStyleClass().addAll("nbfx-slide-bar", side.name().toLowerCase());
        strip.getStyleClass().add("nbfx-slide-strip");
        getChildren().add(strip);
        setVisible(false);
        setManaged(false);
    }

    /** Adds the buttons of the minimized {@code pane}, after the groups already here. */
    void add(TabPane pane) {
        if (groups.containsKey(pane)) {
            return;
        }
        PaneGroup group = new PaneGroup(pane);
        groups.put(pane, group);
        strip.getChildren().add(group.box);
        refreshVisibility();
    }

    /** Removes the buttons of {@code pane}, if they are here. */
    void remove(TabPane pane) {
        PaneGroup group = groups.remove(pane);
        if (group == null) {
            return;
        }
        group.dispose();
        strip.getChildren().remove(group.box);
        if (slid != null && slid.getTabPane() == pane) {
            slid = null;
        }
        refreshVisibility();
    }

    /** Rebuilds the buttons of {@code pane}, after its slid-in tab left the group. */
    void refresh(TabPane pane) {
        PaneGroup group = groups.get(pane);
        if (group != null) {
            group.rebuild();
        }
    }

    /** Shows the button of {@code tab} as the slid-in one ({@code null} for none). */
    void markSlid(Tab tab) {
        Button previous = buttonOf(slid);
        if (previous != null) {
            previous.pseudoClassStateChanged(SLID, false);
        }
        slid = tab;
        Button current = buttonOf(tab);
        if (current != null) {
            current.pseudoClassStateChanged(SLID, true);
        }
    }

    /** Whether the mouse rests on the button of {@code tab}. */
    boolean isHovering(Tab tab) {
        Button button = buttonOf(tab);
        return button != null && button.isHover();
    }

    /** Whether {@code node} is (within) the button of {@code tab}. */
    boolean isButtonOf(Tab tab, Node node) {
        Button button = buttonOf(tab);
        for (Node n = node; button != null && n != null; n = n.getParent()) {
            if (n == button) {
                return true;
            }
        }
        return false;
    }

    private Button buttonOf(Tab tab) {
        TabPane pane = owner.groupOf(tab);
        PaneGroup group = pane == null ? null : groups.get(pane);
        return group == null ? null : group.buttons.get(tab);
    }

    /** Shows the bar while any group has a tab, and marks the groups that follow another. */
    private void refreshVisibility() {
        boolean any = false;
        boolean first = true;
        for (PaneGroup group : groups.values()) {
            boolean shown = !owner.tabsOf(group.pane).isEmpty();
            any |= shown;
            group.box.pseudoClassStateChanged(FOLLOWS, shown && !first);
            if (shown) {
                first = false;
            }
        }
        setVisible(any);
        setManaged(any);
    }

    private Pane newBox() {
        if (SlidingArea.isHorizontal(side)) {
            HBox box = new HBox(SPACING);
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }
        VBox box = new VBox(SPACING);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    /**
     * The button of {@code tab}: its icon (a copy of the one on its header) and its title, which
     * follows the header's; read upwards on the left bar, downwards on the right one.
     */
    private Button button(Tab tab) {
        Label text = new Label();
        text.getStyleClass().add("nbfx-slide-button-text");
        if (tab.getGraphic() instanceof Label label) {
            text.textProperty().bind(label.textProperty());
        } else {
            text.setText(tab.getText());
        }
        Node icon = iconOf(tab);
        Pane content;
        switch (side) {
            case BOTTOM, TOP -> {
                HBox box = new HBox(4);
                box.setAlignment(Pos.CENTER);
                if (icon != null) {
                    box.getChildren().add(icon);
                }
                box.getChildren().add(text);
                content = box;
            }
            case RIGHT -> {
                text.setRotate(90);
                VBox box = new VBox(4);
                box.setAlignment(Pos.CENTER);
                if (icon != null) {
                    box.getChildren().add(icon);
                }
                box.getChildren().add(new Group(text));
                content = box;
            }
            default -> {
                text.setRotate(-90);
                VBox box = new VBox(4);
                box.setAlignment(Pos.CENTER);
                box.getChildren().add(new Group(text));
                if (icon != null) {
                    box.getChildren().add(icon);
                }
                content = box;
            }
        }
        Button button = new Button();
        button.setGraphic(content);
        button.getStyleClass().add("nbfx-slide-button");
        button.setFocusTraversable(false);
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(text.textProperty());
        button.setTooltip(tooltip);
        button.hoverProperty().subscribe(hovered -> owner.buttonHovered(tab, hovered));
        button.setOnAction(_ -> owner.buttonPressed(tab));
        return button;
    }

    /** A copy of the icon on {@code tab}'s header (an image in its label), or {@code null}. */
    private static Node iconOf(Tab tab) {
        if (tab.getGraphic() instanceof Label label && label.getGraphic() instanceof ImageView image) {
            ImageView copy = new ImageView(image.getImage());
            copy.setFitWidth(image.getFitWidth());
            copy.setFitHeight(image.getFitHeight());
            copy.setPreserveRatio(image.isPreserveRatio());
            return copy;
        }
        return null;
    }
}

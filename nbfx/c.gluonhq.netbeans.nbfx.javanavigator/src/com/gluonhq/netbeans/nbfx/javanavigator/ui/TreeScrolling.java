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

import javafx.application.Platform;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.VirtualFlow;

/**
 * Brings a tree row into view with the least scrolling, unlike {@link TreeView#scrollTo}, which pins
 * the row to the top: a row already in the viewport is left where it is, one above it comes to the
 * top edge, one below it to the bottom edge.
 * <p>
 * Every step runs in a pulse of its own and never forces a layout: the rows may have just been
 * replaced, and the flow's cells and cell count only follow in the tree's layout pass, so acting on
 * the flow before that pass has run works on stale cells. A row below the viewport is first placed
 * at the top, then - once its cell has a size - pushed to the bottom edge, and a last pass settles
 * what the horizontal scroll bar appearing in between may still hide. A step is skipped when the
 * selection has moved on to another row.
 */
final class TreeScrolling {

    private TreeScrolling() {
    }

    static <T> void scrollIntoView(TreeView<T> tree, TreeItem<T> item) {
        Platform.runLater(() -> {
            int row = rowOf(tree, item);
            if (row < 0) {
                return;
            }
            if (!(tree.lookup(".virtual-flow") instanceof VirtualFlow<?> flow)) {
                // Not skinned yet: the flow will show the selected row once it is.
                tree.scrollTo(row);
                return;
            }
            boolean below = scrollTowards(flow, row);
            Platform.runLater(() -> {
                int again = rowOf(tree, item);
                if (again >= 0 && tree.lookup(".virtual-flow") instanceof VirtualFlow<?> settled) {
                    settle(settled, again, below);
                    Platform.runLater(() -> {
                        int last = rowOf(tree, item);
                        if (last >= 0 && tree.lookup(".virtual-flow") instanceof VirtualFlow<?> fin) {
                            settle(fin, last, false);
                        }
                    });
                }
            });
        });
    }

    /** The row of {@code item} while it is still the selected one, else {@code -1}. */
    private static <T> int rowOf(TreeView<T> tree, TreeItem<T> item) {
        return tree.getSelectionModel().getSelectedItem() == item ? tree.getRow(item) : -1;
    }

    /**
     * Moves the flow towards {@code row}: to the top edge when above the viewport, the minimum when
     * in it. A row below the viewport is placed at the top for now, and {@code true} is returned so
     * the next pass pushes it to the bottom edge once its cell has a size.
     */
    private static <C extends IndexedCell<?>> boolean scrollTowards(VirtualFlow<C> flow, int row) {
        C first = flow.getFirstVisibleCell();
        C last = flow.getLastVisibleCell();
        if (first == null || last == null || row < first.getIndex()) {
            flow.scrollToTop(row);
            return false;
        }
        if (row > last.getIndex()) {
            flow.scrollToTop(row);
            return true;
        }
        // In the viewport, maybe partly: scrolls the minimum, nothing when it is fully visible.
        flow.scrollTo(row);
        return false;
    }

    /** Pushes {@code row} to the bottom edge ({@code toBottom}), else shifts by what still hides it; nothing for a row not in the flow. */
    private static <C extends IndexedCell<?>> void settle(VirtualFlow<C> flow, int row, boolean toBottom) {
        C cell = flow.getVisibleCell(row);
        if (cell == null) {
            return;
        }
        if (toBottom) {
            flow.scrollToBottom(cell);
        } else {
            flow.scrollTo(cell);
        }
    }
}

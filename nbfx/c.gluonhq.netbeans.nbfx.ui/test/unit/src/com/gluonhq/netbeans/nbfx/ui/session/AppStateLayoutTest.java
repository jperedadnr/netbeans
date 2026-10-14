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
package com.gluonhq.netbeans.nbfx.ui.session;

import com.gluonhq.netbeans.nbfx.docking.DockTrees;
import com.gluonhq.netbeans.nbfx.ui.session.AppState.Layout;
import com.gluonhq.netbeans.nbfx.ui.session.AppState.PaneLayout;
import com.gluonhq.netbeans.nbfx.ui.session.AppState.PaneLayout.Minimized;
import com.gluonhq.netbeans.nbfx.ui.session.AppState.TabEntry;
import com.gluonhq.netbeans.nbfx.ui.session.AppState.TabKind;
import com.gluonhq.netbeans.nbfx.ui.shell.NbfxTabPane.PaneRole;
import java.util.List;
import javafx.geometry.Side;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the persisted form of a pane minimized to a sliding side: it survives a round trip with
 * its side, size and anchor, a line written before sliding sides still reads as a showing pane, and
 * the anchors follow the tree when the editor-only panes are pruned from a layout.
 */
public class AppStateLayoutTest {

    private static final TabEntry PROJECTS = new TabEntry(TabKind.VIEW, "projects", 0, 0, 0);
    private static final TabEntry EDITOR = new TabEntry(TabKind.EDITOR, "/tmp/A.java", 3, 4, 5);

    @Test
    public void minimizedPaneRoundTrips() {
        PaneLayout pane = PaneLayout.docked(PaneRole.DOCKED, List.of(PROJECTS), 0)
                .withMinimized(new Minimized(Side.LEFT, 250, 1, Side.BOTTOM, 0.4));

        PaneLayout read = AppState.parsePane(AppState.serialize(pane));

        assertNotNull(read);
        assertTrue(read.isMinimized());
        assertEquals(pane.minimized(), read.minimized());
        assertEquals(pane.tabs(), read.tabs());
        assertEquals(PaneRole.DOCKED, read.role());
    }

    @Test
    public void anchorIntoALeafRoundTrips() {
        PaneLayout pane = PaneLayout.docked(PaneRole.DOCKED, List.of(EDITOR), 0)
                .withMinimized(new Minimized(Side.BOTTOM, 180, 0, null, Double.NaN));

        PaneLayout read = AppState.parsePane(AppState.serialize(pane));

        assertNotNull(read);
        assertEquals(Side.BOTTOM, read.minimized().side());
        assertEquals(180, read.minimized().size(), 0.0);
        assertEquals(0, read.minimized().anchorIndex());
        assertNull("an anchor into a leaf has no side", read.minimized().anchorSide());
    }

    @Test
    public void anchorNextToAMinimizedPaneRoundTrips() {
        PaneLayout pane = PaneLayout.docked(PaneRole.DOCKED, List.of(PROJECTS), 0)
                .withMinimized(new Minimized(Side.LEFT, 200, Minimized.NO_ANCHOR, Side.BOTTOM, 0.35, 3));

        PaneLayout read = AppState.parsePane(AppState.serialize(pane));

        assertNotNull(read);
        assertEquals(pane.minimized(), read.minimized());
        assertTrue(read.minimized().hasAnchor());
        assertEquals(3, read.minimized().anchorPane());
    }

    @Test
    public void paneWithoutAnchorRoundTrips() {
        PaneLayout pane = PaneLayout.docked(PaneRole.NAVIGATOR, List.of(PROJECTS), 0)
                .withMinimized(new Minimized(Side.RIGHT, 300, Minimized.NO_ANCHOR, null, Double.NaN));

        PaneLayout read = AppState.parsePane(AppState.serialize(pane));

        assertNotNull(read);
        assertFalse(read.minimized().hasAnchor());
        assertEquals(Side.RIGHT, read.minimized().side());
    }

    @Test
    public void lineWrittenBeforeSlidingSidesReadsAsShowing() {
        PaneLayout read = AppState.parsePane("NAVIGATOR\tNaN\tNaN\tNaN\tNaN\t0\tVIEW\rprojects\r0\r0\r0");

        assertNotNull(read);
        assertFalse(read.isMinimized());
        assertEquals(List.of(PROJECTS), read.tabs());
    }

    @Test
    public void editorAndDetachedPanesNeverReadAsMinimized() {
        PaneLayout main = AppState.parsePane("MAIN@LEFT@200\tNaN\tNaN\tNaN\tNaN\t-1");

        assertNotNull(main);
        assertFalse(main.isMinimized());
    }

    @Test
    public void prunedLayoutRemapsTheAnchorsOfMinimizedPanes() {
        // Leaves: a docked pane of editors only (dropped), the navigator, the editor pane.
        Layout layout = new Layout(List.of(
                PaneLayout.docked(PaneRole.DOCKED, List.of(EDITOR), 0),
                PaneLayout.docked(PaneRole.NAVIGATOR, List.of(PROJECTS), 0),
                PaneLayout.docked(PaneRole.MAIN, List.of(EDITOR), 0),
                PaneLayout.docked(PaneRole.DOCKED, List.of(PROJECTS), 0)
                        .withMinimized(new Minimized(Side.LEFT, 200, 2, Side.LEFT, 0.3)),
                PaneLayout.docked(PaneRole.DOCKED, List.of(PROJECTS), 0)
                        .withMinimized(new Minimized(Side.LEFT, 200, 0, Side.BOTTOM, 0.5)),
                PaneLayout.docked(PaneRole.DOCKED, List.of(PROJECTS), 0)
                        .withMinimized(new Minimized(Side.LEFT, 200, Minimized.NO_ANCHOR, Side.BOTTOM, 0.5, 3))),
                -1, DockTrees.parse("H[0.2000,0.5000](L,P:NAVIGATOR,P:MAIN)"));

        Layout pruned = layout.withoutEditors();

        assertEquals("H[0.3750](P:NAVIGATOR,P:MAIN)", DockTrees.format(pruned.dock()));
        List<PaneLayout> minimized = pruned.panes().stream().filter(PaneLayout::isMinimized).toList();
        assertEquals(3, minimized.size());
        assertEquals("anchored at the editor pane, which moved up one leaf", 1, minimized.get(0).minimized().anchorIndex());
        assertEquals(Side.LEFT, minimized.get(0).minimized().anchorSide());
        assertFalse("anchored at the dropped leaf", minimized.get(1).minimized().hasAnchor());
        assertEquals("anchored next to the first minimized pane, which moved up one pane", 2,
                minimized.get(2).minimized().anchorPane());
        assertEquals(2, pruned.dockedPanes().size());
    }
}

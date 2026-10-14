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
package com.gluonhq.netbeans.nbfx.docking;

import com.gluonhq.netbeans.nbfx.docking.DockArea.Anchor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies minimizing panes out of a {@link DockArea} and restoring them: a minimized pane leaves
 * the tree and comes back where it was with its share, a docked pane that empties while minimized is
 * disposed of, a pane anchored into another rejoins it, a minimized permanent pane does not come back
 * on its own when it receives content, and {@link DockArea#applyTree} restores everything first.
 * Runs on the headless JavaFX toolkit, as the area defers its clean-up to later pulses.
 */
public class DockAreaTest {

    private static final AtomicBoolean FX_STARTED = new AtomicBoolean();

    /** Panes are stack panes; a pane is empty when it has no children. */
    private static final class Host implements DockHost<StackPane> {

        final List<StackPane> disposed = new ArrayList<>();

        @Override
        public StackPane createDockedPane() {
            return new StackPane();
        }

        @Override
        public void disposeDockedPane(StackPane pane) {
            disposed.add(pane);
        }

        @Override
        public ObservableValue<Boolean> isEmpty(StackPane pane) {
            return Bindings.isEmpty(pane.getChildren());
        }

        @Override
        public void moveContent(StackPane from, StackPane into) {
            into.getChildren().addAll(List.copyOf(from.getChildren()));
            from.getChildren().clear();
        }
    }

    /** An area of a navigator pane (holding content) and a main pane. */
    private static final class Fixture {

        final Host host = new Host();
        final StackPane navigator = new StackPane(new Region());
        final StackPane main = new StackPane();
        final DockArea<StackPane> area;

        Fixture() {
            SequencedMap<String, StackPane> primaries = new LinkedHashMap<>();
            primaries.put("NAVIGATOR", navigator);
            primaries.put("MAIN", main);
            area = new DockArea<>(host, primaries, List.of(0.3));
        }
    }

    @Test
    public void minimizedPaneLeavesTheTreeAndComesBackWithItsShare() throws Exception {
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            f.area.minimize(f.navigator);
            assertEquals("P:MAIN", DockTrees.format(f.area.tree()));
            assertTrue(f.area.isMinimized(f.navigator));
            assertTrue(f.area.isPrimaryHidden(f.navigator));
            assertEquals(List.of(f.navigator), f.area.minimizedPanes());
            Anchor<StackPane> anchor = f.area.anchorOf(f.navigator);
            assertNotNull(anchor);
            assertSame(f.main, anchor.leaf());
            assertEquals(Side.LEFT, anchor.side());
            assertEquals(0.3, anchor.share(), 0.001);

            f.area.restore(f.navigator);
            assertFalse(f.area.isMinimized(f.navigator));
            assertTrue(f.area.minimizedPanes().isEmpty());
            assertEquals(List.of(f.navigator, f.main), f.area.leaves());
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            return null;
        });
    }

    @Test
    public void dockedPaneMinimizedBelowTheNavigatorComesBackBelowIt() throws Exception {
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            StackPane docked = f.area.split(f.navigator, Side.BOTTOM);
            docked.getChildren().add(new Region());
            assertEquals("H[0.3000](V[0.6500](P:NAVIGATOR,L),P:MAIN)", DockTrees.format(f.area.tree()));

            f.area.minimize(docked);
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            Anchor<StackPane> anchor = f.area.anchorOf(docked);
            assertSame(f.navigator, anchor.leaf());
            assertEquals(Side.BOTTOM, anchor.side());
            assertEquals(0.35, anchor.share(), 0.001);

            f.area.restore(docked);
            assertEquals("H[0.3000](V[0.6500](P:NAVIGATOR,L),P:MAIN)", DockTrees.format(f.area.tree()));
            assertSame(docked, f.area.leaves().get(1));
            return null;
        });
    }

    @Test
    public void dockedPaneThatEmptiesWhileMinimizedIsDisposed() throws Exception {
        Fixture f = onFx(Fixture::new);
        StackPane docked = onFx(() -> {
            StackPane pane = f.area.split(f.navigator, Side.BOTTOM);
            pane.getChildren().add(new Region());
            f.area.minimize(pane);
            pane.getChildren().clear();
            return pane;
        });
        pulse();
        onFx(() -> {
            assertFalse(f.area.isMinimized(docked));
            assertEquals(List.of(docked), f.host.disposed);
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            return null;
        });
    }

    @Test
    public void paneAnchoredIntoAnotherRejoinsItOnRestore() throws Exception {
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            // A single component minimized out of the navigator pane: a pane of its own, never docked.
            StackPane alone = f.host.createDockedPane();
            Node component = new Region();
            alone.getChildren().add(component);
            f.area.minimize(alone, Anchor.into(f.navigator));
            assertTrue(f.area.isMinimized(alone));
            assertTrue(f.area.anchorOf(alone).joins());
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));

            f.area.restore(alone);
            assertTrue(f.navigator.getChildren().contains(component));
            assertEquals(List.of(alone), f.host.disposed);
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            return null;
        });
    }

    @Test
    public void minimizedPrimaryStaysOutWhenItReceivesContent() throws Exception {
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            f.area.minimize(f.navigator);
            f.navigator.getChildren().clear();
            return null;
        });
        pulse();
        onFx(() -> {
            assertTrue("an emptied minimized primary stays minimized", f.area.isMinimized(f.navigator));
            f.navigator.getChildren().add(new Region());
            assertEquals("content does not bring a minimized primary back", "P:MAIN", DockTrees.format(f.area.tree()));
            assertNull(f.area.paneBelow(f.navigator));

            f.area.restore(f.navigator);
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            return null;
        });
    }

    @Test
    public void emptyMinimizedPrimaryRestoredStaysHiddenUntilItFills() throws Exception {
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            f.area.minimize(f.navigator);
            f.navigator.getChildren().clear();
            f.area.restore(f.navigator);
            assertFalse(f.area.isMinimized(f.navigator));
            assertEquals("P:MAIN", DockTrees.format(f.area.tree()));
            f.navigator.getChildren().add(new Region());
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            return null;
        });
    }

    @Test
    public void applyTreeRestoresMinimizedPanesFirst() throws Exception {
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            f.area.minimize(f.navigator);
            f.area.applyTree(f.area.defaultTree());
            assertTrue(f.area.minimizedPanes().isEmpty());
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            return null;
        });
    }

    @Test
    public void twoPanesThatLeftInTurnComeBackAsTheyWereWhicheverReturnsFirst() throws Exception {
        for (boolean navigatorFirst : new boolean[] {true, false}) {
            for (boolean restoreNavigatorFirst : new boolean[] {true, false}) {
                Fixture f = onFx(Fixture::new);
                String order = "minimize " + (navigatorFirst ? "navigator" : "docked") + " first, restore "
                        + (restoreNavigatorFirst ? "navigator" : "docked") + " first";
                onFx(() -> {
                    StackPane docked = f.area.split(f.navigator, Side.BOTTOM);
                    docked.getChildren().add(new Region());
                    assertEquals("H[0.3000](V[0.6500](P:NAVIGATOR,L),P:MAIN)", DockTrees.format(f.area.tree()));
                    f.area.minimize(navigatorFirst ? f.navigator : docked);
                    f.area.minimize(navigatorFirst ? docked : f.navigator);
                    assertEquals(order, "P:MAIN", DockTrees.format(f.area.tree()));
                    f.area.restore(restoreNavigatorFirst ? f.navigator : docked);
                    f.area.restore(restoreNavigatorFirst ? docked : f.navigator);
                    assertEquals(order, "H[0.3000](V[0.6500](P:NAVIGATOR,L),P:MAIN)", DockTrees.format(f.area.tree()));
                    assertEquals(order, List.of(f.navigator, docked, f.main), f.area.leaves());
                    return null;
                });
            }
        }
    }

    @Test
    public void paneAnchoredIntoAMinimizedPaneRestoresThatPaneAndJoinsIt() throws Exception {
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            StackPane alone = f.host.createDockedPane();
            Node component = new Region();
            alone.getChildren().add(component);
            f.area.minimize(alone, Anchor.into(f.navigator));
            f.area.minimize(f.navigator);
            assertEquals("P:MAIN", DockTrees.format(f.area.tree()));

            f.area.restore(alone);
            assertFalse(f.area.isMinimized(f.navigator));
            assertTrue(f.navigator.getChildren().contains(component));
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            return null;
        });
    }

    @Test
    public void mainPaneCannotBeMinimized() throws Exception {
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            try {
                f.area.minimize(f.main);
                fail("the main pane was minimized");
            } catch (IllegalArgumentException expected) {
                // as documented
            }
            assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(f.area.tree()));
            return null;
        });
    }

    /** Lets the pulses the area deferred work to run. */
    private static void pulse() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            onFx(() -> null);
        }
    }

    private static <T> T onFx(Supplier<T> task) throws InterruptedException {
        ensureFxStarted();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) {
            fail("timed out waiting for the JavaFX thread");
        }
        Throwable failure = error.get();
        if (failure instanceof AssertionError assertion) {
            throw assertion;
        }
        if (failure != null) {
            throw new AssertionError(failure);
        }
        return result.get();
    }

    private static void ensureFxStarted() throws InterruptedException {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
        }
    }
}

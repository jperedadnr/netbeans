package com.gluonhq.netbeans.nbfx.launcher.session;

import com.gluonhq.netbeans.nbfx.docking.DockArea;
import com.gluonhq.netbeans.nbfx.docking.DockTree;
import com.gluonhq.netbeans.nbfx.docking.DockTrees;
import com.gluonhq.netbeans.nbfx.launcher.ui.Docking;
import com.gluonhq.netbeans.nbfx.launcher.ui.EditorSplit;
import com.gluonhq.netbeans.nbfx.launcher.ui.NbfxTabPane;

import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.NavigatorProvider;
import com.gluonhq.netbeans.nbfx.launcher.session.AppState.Layout;
import com.gluonhq.netbeans.nbfx.launcher.session.AppState.PaneLayout;
import com.gluonhq.netbeans.nbfx.launcher.session.AppState.SplitEntry;
import com.gluonhq.netbeans.nbfx.launcher.session.AppState.TabEntry;
import com.gluonhq.netbeans.nbfx.launcher.session.AppState.TabKind;
import com.gluonhq.netbeans.nbfx.launcher.ui.NbfxTabPane.PaneRole;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Session orchestration on top of {@link AppState}: captures the live window layout and tree state
 * into the persistence layer and restores them back onto the scene graph. Unlike {@link AppState},
 * this reaches into the global {@link Lookup}, {@link ContentManager}, {@link EditorDocument},
 * {@link NbfxTabPane} and {@link Platform#runLater}, and is therefore FX-thread-bound and
 * scene-graph-coupled.
 *
 * <p>Layout is persisted <em>per pane</em> rather than per tab kind, because the user may drag any
 * tab into any pane: the main pane can hold navigator tabs, the navigator pane can hold editors, and
 * a detached window can hold a mix of both. Each pane is identified by its
 * {@link PaneRole} (detached panes additionally by their position in the list).</p>
 */
public final class SessionRestorer {

    private static final Logger LOG = Logger.getLogger(SessionRestorer.class.getName());

    private final AppState appState;

    /**
     * Resolves a view provider id to its tab, reusing the existing one wherever it currently lives
     * and creating it only if it has none. Supplied by the launcher, which owns the providers.
     */
    private final Function<String, Tab> viewTabs;

    public SessionRestorer(AppState appState, Function<String, Tab> viewTabs) {
        this.appState = appState;
        this.viewTabs = viewTabs;
    }

    // --- Capture ------------------------------------------------------------

    /**
     * Captures the given projects' expanded tree nodes and the current window layout so the whole
     * session can be restored at the next launch. Every open project's tree state is captured under
     * its own key, while the layout - which spans every project, since one window holds the tabs of
     * all of them - is captured once. Must be called on the FX thread while the windows are still
     * showing.
     */
    public void captureSession(Collection<File> projects,
            Collection<? extends NavigatorProvider> providers) {
        if (projects != null) {
            for (File project : projects) {
                captureExpansion(project, providers);
            }
        }
        captureLayout();
    }

    private void captureExpansion(File project, Collection<? extends NavigatorProvider> providers) {
        if (project == null || providers == null) {
            return;
        }
        FileObject root = FileUtil.toFileObject(FileUtil.normalizeFile(project));
        if (root == null) {
            return;
        }
        List<String> expanded = new ArrayList<>();
        String selected = null;
        for (NavigatorProvider provider : providers) {
            // Only this project's own nodes: with several projects open the providers hold one tree
            // per root, and each project's state is persisted under its own key.
            expanded.addAll(provider.getExpandedPaths(root));
            if (selected == null) {
                selected = provider.getSelectedPath(root);
            }
        }
        appState.setExpandedNodes(project.getPath(), expanded);
        appState.setSelectedNode(project.getPath(), selected);
    }

    /**
     * Persists the current arrangement of every pane and the tabs it holds, and the shape of the dock
     * area - pruned of the docked panes that are not persisted, so its leaves still match.
     */
    private void captureLayout() {
        List<PaneLayout> panes = new ArrayList<>();
        Set<TabPane> skipped = new HashSet<>();
        TabPane focusedPane = NbfxTabPane.focusedPane();
        int focused = -1;
        for (TabPane pane : NbfxTabPane.panesByRole()) {
            PaneLayout captured = capturePane(pane);
            if (captured == null) {
                skipped.add(pane);
                continue;
            }
            if (pane == focusedPane) {
                focused = panes.size();
            }
            panes.add(captured);
        }
        DockTree dock = null;
        DockArea<TabPane> area = Docking.area();
        if (area != null) {
            List<TabPane> leaves = area.leaves();
            dock = area.tree().retainLeaves(i -> !skipped.contains(leaves.get(i)));
        }
        appState.setSessionLayout(new Layout(panes, focused, dock));
        int captured = focused;
        LOG.info(() -> "Captured layout: " + panes.size() + " panes, focused index " + captured
                + " (" + (captured >= 0 ? panes.get(captured).role() : "none") + ")");
        LOG.fine(NbfxTabPane::describeFocus);
    }

    /**
     * Captures one pane, or {@code null} if it should not be persisted: a detached pane that is not
     * (or no longer) in a window, or a detached or docked pane left with no persistable tab.
     */
    private static PaneLayout capturePane(TabPane pane) {
        PaneRole role = NbfxTabPane.roleOf(pane);
        Stage stage = role == PaneRole.DETACHED ? NbfxTabPane.stageOf(pane) : null;
        if (role == PaneRole.DETACHED && stage == null) {
            return null;
        }
        List<TabEntry> tabs = new ArrayList<>();
        Tab selected = pane.getSelectionModel().getSelectedItem();
        int activeIndex = -1;
        for (Tab tab : pane.getTabs()) {
            TabEntry entry = entryFor(tab);
            if (entry == null) {
                continue;
            }
            if (tab == selected) {
                activeIndex = tabs.size();
            }
            tabs.add(entry);
        }
        if (tabs.isEmpty() && !role.isPermanent()) {
            return null;
        }
        if (role == PaneRole.DETACHED) {
            return new PaneLayout(role, stage.getX(), stage.getY(),
                    stage.getWidth(), stage.getHeight(), tabs, activeIndex);
        }
        return PaneLayout.docked(role, tabs, activeIndex);
    }

    /** The persistable descriptor of {@code tab}, or {@code null} if it carries no stable id. */
    private static TabEntry entryFor(Tab tab) {
        EditorDocument document = NbfxTabPane.documentOf(tab);
        if (document != null) {
            String path = pathOf(document.getFileObject());
            if (path == null) {
                return null;
            }
            EditorDocument second = EditorSplit.secondOf(tab);
            SplitEntry split = second == null ? null : new SplitEntry(EditorSplit.orientationOf(tab),
                    second.getTopParagraph(), second.getCaretParagraph(), second.getCaretColumn());
            return new TabEntry(TabKind.EDITOR, path, document.getTopParagraph(), document.getCaretParagraph(),
                    document.getCaretColumn(), split);
        }
        String viewId = NbfxTabPane.viewId(tab);
        return viewId == null ? null : TabEntry.view(viewId);
    }

    // --- Restore ------------------------------------------------------------

    /**
     * The persisted session layout stripped of its editor tabs, holding view tabs only. Used at
     * start-up to arrange the navigator pane before the session's projects have loaded; the editors
     * come back with {@link #restoreSession(Consumer)} once they have.
     */
    public Layout startupLayout() {
        return appState.getSessionLayout().withoutEditors();
    }

    /**
     * Restores the persisted session: reopens the editors of every project, moves the view tabs
     * into the panes they were left in and recreates the detached windows at their bounds. Files that
     * no longer exist - typically those of a project that is no longer open - are skipped. Safe to
     * call on the FX thread once every project of the session has finished loading.
     *
     * @param done run on the FX thread once the layout has been applied, with the document of the
     *             tab that ended up focused ({@code null} when that is not an editor); may be
     *             {@code null} itself
     */
    public void restoreSession(Consumer<EditorDocument> done) {
        Layout layout = appState.getSessionLayout();
        if (layout.isEmpty()) {
            runSafely(done, null);
            return;
        }
        ContentManager cm = Lookup.getDefault().lookup(ContentManager.class);
        if (cm == null) {
            LOG.warning("No ContentManager found; cannot restore the window layout");
            runSafely(done, null);
            return;
        }
        openEditors(cm, layout);
        Platform.runLater(() -> {
            Map<Tab, TabEntry> editors = new IdentityHashMap<>();
            TabPane focusPane = applyLayout(layout, cm, editors);
            // Every detached window is shown after the primary one, so it ends up in front whether
            // or not it should be. Fall back to the main pane, which also covers layouts persisted
            // before the focused pane was recorded.
            if (focusPane == null) {
                focusPane = NbfxTabPane.paneWithRole(PaneRole.MAIN);
            }
            restoreViews(editors);
            focusActivePane(focusPane);
            runSafely(done, focusedDocument(focusPane));
        });
    }

    /** The document of the tab selected in {@code pane}, or {@code null} if it holds no editor. */
    private static EditorDocument focusedDocument(TabPane pane) {
        return pane == null ? null : NbfxTabPane.documentOf(pane.getSelectionModel().getSelectedItem());
    }

    private static void runSafely(Consumer<EditorDocument> done, EditorDocument focused) {
        if (done != null) {
            done.accept(focused);
        }
    }

    /**
     * Opens every editor the layout holds. Each one attaches its tab in its own
     * {@link Platform#runLater}, so a block queued after this call sees them all and can distribute
     * them. Paths are de-duplicated because {@code openFile} only finds an existing tab once it has
     * been attached, so opening the same file twice in one block would create two tabs for it; the
     * further entries of a file (its clones) are recreated when the layout is applied.
     */
    private static void openEditors(ContentManager cm, Layout layout) {
        Set<String> opened = new LinkedHashSet<>();
        for (PaneLayout pane : layout.panes()) {
            for (TabEntry tab : pane.tabs()) {
                if (tab.kind() != TabKind.EDITOR || !opened.add(tab.id())) {
                    continue;
                }
                FileObject fo = toFileObject(tab.id());
                if (fo == null) {
                    LOG.warning("Skipping restore of missing file: " + tab.id());
                    continue;
                }
                cm.openFile(fo, null);
            }
        }
    }

    /**
     * Moves the existing tabs into the panes described by {@code layout}. The dock area is first given
     * the shape the layout recorded (creating the docked panes its tabs call for), then the docked
     * panes are filled in place, and each detached pane is recreated as a new window. View tabs the
     * layout does not mention were closed by the user and are removed.
     *
     * @param editors receives every editor tab placed, with the entry it was restored from
     * @return the pane that should receive focus, or {@code null} when the layout does not say
     */
    private TabPane applyLayout(Layout layout, ContentManager cm, Map<Tab, TabEntry> editors) {
        Set<Tab> placed = new LinkedHashSet<>();
        PaneLayout focusedPane = layout.focusedPane();
        TabPane focusTarget = null;
        Map<PaneLayout, TabPane> dockedTargets = shapeArea(layout);
        for (PaneLayout pane : layout.panes()) {
            List<Tab> tabs = resolveTabs(pane, cm, placed, editors);
            placed.addAll(tabs);
            if (pane.role() == PaneRole.DETACHED) {
                if (tabs.isEmpty()) {
                    continue;
                }
                double[] bounds = AppState.clampBounds(pane.x(), pane.y(), pane.width(), pane.height());
                TabPane detached = NbfxTabPane.openDetachedWindow(tabs,
                        activeTab(tabs, pane.activeIndex()),
                        bounds[0], bounds[1], bounds[2], bounds[3]);
                if (pane == focusedPane) {
                    focusTarget = detached;
                }
            } else {
                if (tabs.isEmpty() && !pane.role().isPermanent()) {
                    continue;
                }
                TabPane target = dockedTargets.containsKey(pane)
                        ? dockedTargets.get(pane)
                        : NbfxTabPane.paneWithRole(pane.role());
                if (target == null) {
                    LOG.warning("No " + pane.role() + " pane to restore into");
                    continue;
                }
                // Only tabs that are not already in place are moved, so re-applying an unchanged
                // layout leaves the panes (and the editors' skins) untouched. Tabs the layout does
                // not mention stay, pushed after the ones it does.
                for (int i = 0; i < tabs.size(); i++) {
                    if (target.getTabs().indexOf(tabs.get(i)) != i) {
                        NbfxTabPane.moveTab(target, tabs.get(i), i);
                    }
                }
                Tab active = activeTab(tabs, pane.activeIndex());
                if (active != null) {
                    target.getSelectionModel().select(active);
                }
                if (pane == focusedPane) {
                    focusTarget = target;
                }
            }
        }
        closeUnplacedViewTabs(placed);
        return focusTarget;
    }

    /**
     * Gives the dock area the shape {@code layout} recorded and maps every docked pane of the layout
     * to the live pane it fills. A permanent pane the recorded tree leaves out was hidden (empty) when
     * captured and stays out until it receives a tab. When the recorded tree does not fit the panes
     * (or there is none, as in layouts written before docking) the docked panes are stacked below the
     * permanent ones in their default arrangement instead.
     */
    private static Map<PaneLayout, TabPane> shapeArea(Layout layout) {
        Map<PaneLayout, TabPane> targets = new IdentityHashMap<>();
        DockArea<TabPane> area = Docking.area();
        List<PaneLayout> docked = layout.dockedPanes();
        if (area == null || docked.isEmpty()) {
            return targets;
        }
        DockTree recorded = layout.dock();
        DockTree tree = recorded;
        List<PaneLayout> leafPanes = recorded == null || !area.accepts(recorded) ? null : layout.leafPanes(recorded);
        if (leafPanes != null) {
            // A permanent pane the tree leaves out must have been hidden, so must be empty.
            for (PaneLayout pane : docked) {
                if (pane.role().isPermanent() && !leafPanes.contains(pane) && !pane.tabs().isEmpty()) {
                    leafPanes = null;
                    break;
                }
            }
        }
        if (leafPanes == null) {
            if (recorded != null) {
                LOG.warning(() -> "The recorded dock tree " + DockTrees.format(recorded)
                        + " does not fit its " + docked.size() + " panes; stacking them");
            }
            tree = fallbackTree(area, docked);
            leafPanes = layout.leafPanes(tree);
        }
        List<TabPane> leaves = area.applyTree(tree);
        for (int i = 0; i < leafPanes.size(); i++) {
            targets.put(leafPanes.get(i), leaves.get(i));
        }
        for (PaneLayout pane : docked) {
            if (!targets.containsKey(pane) && pane.role().isPermanent() && area.primary(pane.role().name()) != null) {
                targets.put(pane, area.primary(pane.role().name()));
            }
        }
        return targets;
    }

    /**
     * The permanent panes in their default arrangement with the layout's {@code DOCKED} panes
     * stacked below - always a tree the area accepts and {@code layout} can match leaves to.
     */
    private static DockTree fallbackTree(DockArea<TabPane> area, List<PaneLayout> docked) {
        long dockedCount = docked.stream().filter(pane -> pane.role() == PaneRole.DOCKED).count();
        DockTree base = area.defaultTree();
        if (dockedCount == 0) {
            return base;
        }
        List<String> stack = new ArrayList<>();
        for (int i = 0; i < dockedCount; i++) {
            stack.add(null);
        }
        DockTree below = DockTrees.stacked(stack);
        double share = 1.0 / (dockedCount + 1);
        return new DockTree.Split(Orientation.VERTICAL, List.of(base, below), List.of(1 - share * dockedCount));
    }

    /**
     * The tabs of {@code pane}, in order, skipping entries whose tab could not be resolved. A file
     * listed more than once in the layout was open in clones: each further entry takes an editor tab
     * of the file not yet {@code claimed} by an earlier entry, or has a new clone opened when there is
     * none left, so re-applying an unchanged layout reuses the existing tabs.
     */
    private List<Tab> resolveTabs(PaneLayout pane, ContentManager cm, Set<Tab> claimed, Map<Tab, TabEntry> editors) {
        List<Tab> tabs = new ArrayList<>();
        for (TabEntry entry : pane.tabs()) {
            Tab tab = entry.kind() == TabKind.VIEW
                    ? viewTabs.apply(entry.id())
                    : editorTab(entry.id(), cm, claimed, tabs);
            if (tab != null && !tabs.contains(tab)) {
                tabs.add(tab);
                if (entry.kind() == TabKind.EDITOR) {
                    editors.put(tab, entry);
                }
            }
        }
        return tabs;
    }

    private static Tab activeTab(List<Tab> tabs, int activeIndex) {
        return activeIndex >= 0 && activeIndex < tabs.size() ? tabs.get(activeIndex) : null;
    }

    /**
     * The first editor tab of {@code path} that neither {@code claimed} (earlier panes) nor
     * {@code taken} (this pane) hold; when every tab of the file is spoken for, a clone of the last
     * one is opened and its tab returned.
     */
    private static Tab editorTab(String path, ContentManager cm, Set<Tab> claimed, List<Tab> taken) {
        FileObject fo = toFileObject(path);
        if (fo == null) {
            return null;
        }
        Tab last = null;
        for (TabPane pane : NbfxTabPane.tabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (fo.equals(tab.getUserData())) {
                    if (!claimed.contains(tab) && !taken.contains(tab)) {
                        return tab;
                    }
                    last = tab;
                }
            }
        }
        if (last == null) {
            return null;
        }
        EditorDocument clone = cm.cloneDocument(NbfxTabPane.documentOf(last));
        return clone == null ? null : NbfxTabPane.findTab(clone).orElse(null);
    }

    /**
     * Removes every view tab the restored layout did not claim. Such a tab was closed by the user
     * before the layout was captured; without this it would linger from the previous project's
     * arrangement (or from the default one built at start-up).
     */
    private static void closeUnplacedViewTabs(Set<Tab> placed) {
        for (TabPane pane : NbfxTabPane.tabPanes()) {
            pane.getTabs().removeIf(tab -> NbfxTabPane.viewId(tab) != null && !placed.contains(tab));
        }
    }

    /**
     * Restores the scroll and caret position of each reopened editor (each clone keeps its own), and
     * the split of the tabs that had one, with the position of their second editor.
     */
    private static void restoreViews(Map<Tab, TabEntry> editors) {
        editors.forEach((tab, entry) -> {
            EditorDocument document = NbfxTabPane.documentOf(tab);
            if (document == null) {
                return;
            }
            document.restoreView(entry.topParagraph(), entry.caretParagraph(), entry.caretColumn());
            SplitEntry split = entry.split();
            if (split != null && !EditorSplit.isSplit(tab)) {
                EditorSplit.split(tab, split.orientation());
                EditorDocument second = EditorSplit.secondOf(tab);
                if (second != null) {
                    second.restoreView(split.topParagraph(), split.caretParagraph(), split.caretColumn());
                }
            }
        });
    }

    /**
     * Brings {@code pane}'s window to the front and gives focus to the tab selected in it - the pane
     * that was active when the layout was captured, which is not necessarily the main one since
     * editors can be dragged into any pane.
     * <p>
     * Every pane already focuses its own selected tab when its window becomes active, so focusing
     * ours right away would just be undone by those handlers. Waiting for the window to actually
     * become active and listening after them (listeners run in registration order) makes ours the
     * one that wins.
     */
    private static void focusActivePane(TabPane pane) {
        if (pane == null) {
            return;
        }
        Stage stage = NbfxTabPane.stageOf(pane);
        if (stage == null || stage.isFocused()) {
            // Already active: no focus change is coming, so nothing will compete with this.
            NbfxTabPane.focusDocument(pane.getSelectionModel().getSelectedItem());
            return;
        }
        stage.focusedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> obs, Boolean was, Boolean active) {
                if (Boolean.TRUE.equals(active)) {
                    stage.focusedProperty().removeListener(this);
                    NbfxTabPane.focusDocument(pane.getSelectionModel().getSelectedItem());
                }
            }
        });
        stage.toFront();
        stage.requestFocus();
    }

    private static String pathOf(FileObject fo) {
        File f = FileUtil.toFile(fo);
        return f != null ? f.getAbsolutePath() : null;
    }

    private static FileObject toFileObject(String path) {
        File f = new File(path);
        return f.isFile() ? FileUtil.toFileObject(f) : null;
    }

}

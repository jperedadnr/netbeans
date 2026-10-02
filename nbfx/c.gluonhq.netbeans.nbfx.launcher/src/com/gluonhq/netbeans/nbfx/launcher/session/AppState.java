package com.gluonhq.netbeans.nbfx.launcher.session;

import com.gluonhq.netbeans.nbfx.docking.DockTree;
import com.gluonhq.netbeans.nbfx.docking.DockTrees;
import com.gluonhq.netbeans.nbfx.launcher.ui.NbfxTabPane;
import com.gluonhq.netbeans.nbfx.launcher.ui.ToolBarContainer;

import com.gluonhq.netbeans.nbfx.api.editor.EditorSettings;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.openide.util.NbPreferences;

/**
 * Single entry point for persisting and restoring application UI state via
 * {@link NbPreferences} (stored in the platform userdir). All preference access
 * routes through this helper; restore is best-effort and never blocks the app.
 *
 * <p>The current window bounds and split-divider position are kept in a
 * {@code volatile} snapshot updated by FX-thread listeners, so {@link #save()}
 * can run from any thread (e.g. a JVM shutdown hook) without touching live
 * scene-graph nodes. {@code save()} is idempotent.</p>
 */
public final class AppState {

    private static final Logger LOG = Logger.getLogger(AppState.class.getName());

    private static final String WINDOW_X = "window.x";
    private static final String WINDOW_Y = "window.y";
    private static final String WINDOW_WIDTH = "window.width";
    private static final String WINDOW_HEIGHT = "window.height";
    private static final String WINDOW_MAXIMIZED = "window.maximized";
    /** Slack, in pixels, when checking whether a maximized stage still fills its screen. */
    private static final double MAXIMIZED_TOLERANCE = 4;
    private static final String TOOLBARS = "toolbars.arrangement";
    private static final String TOOLBARS_HIDDEN = "toolbars.hidden";
    private static final String VIEW_SHOW_LINE_NUMBERS = "view.showLineNumbers";
    private static final String VIEW_SHOW_BREADCRUMBS = "view.showBreadcrumbs";
    private static final String RECENT_PROJECTS = "project.recent";
    /** The projects that were open at exit, one path per line. */
    private static final String PROJECT_OPEN = "project.open";
    /** The path of the project that was selected at exit. */
    private static final String PROJECT_SELECTED = "project.selected";
    private static final String TREE_EXPANDED_NODE = "tree.expanded";
    private static final String TREE_SELECTED_NODE = "tree.selected";
    private static final String LAYOUT_NODE = "layout";

    /**
     * Key under which the whole session's layout is stored inside {@link #LAYOUT_NODE}. The layout
     * spans every open project, since one window holds the tabs of all of them. Legacy per-project
     * keys are hexadecimal hashes, so they can never collide with this name.
     */
    private static final String LAYOUT_SESSION_KEY = "session";

    /** Legacy key of the project-independent layout, read once by {@link #migrateLegacySession()}. */
    private static final String LAYOUT_LEGACY_GLOBAL_KEY = "global";

    /** Field separator between a pane's attributes and its tabs within a serialized layout line. */
    private static final String FIELD_SEP = "\t";
    /** Field separator between the attributes of a single tab within a serialized pane field. */
    private static final String TAB_SEP = "\r";

    private static final String[] EMPTY_LINES = new String[0];

    /** Default main-window size used when nothing is persisted. */
    public static final double DEFAULT_WIDTH = 1200;
    public static final double DEFAULT_HEIGHT = 800;

    /** Default detached-window size used when a persisted one is off-screen or invalid. */
    private static final double DEFAULT_DETACHED_WIDTH = 900;
    private static final double DEFAULT_DETACHED_HEIGHT = 650;

    /** Maximum number of recent projects retained. */
    static final int MAX_RECENT_PROJECTS = 10;

    private final Preferences prefs;

    // Snapshot of the current UI state, kept up to date on the FX thread.
    private volatile double winX = Double.NaN, winY = Double.NaN;
    private volatile double winW = DEFAULT_WIDTH, winH = DEFAULT_HEIGHT;
    private volatile boolean winMax = false;
    private volatile String toolbarArrangement = null;
    private volatile String toolbarHidden = null;
    private volatile boolean showLineNumbers = true;
    private volatile boolean showBreadcrumbs = true;
    private final AtomicBoolean saved = new AtomicBoolean();

    public AppState() {
        this(NbPreferences.forModule(AppState.class));
    }

    public AppState(Preferences prefs) {
        this.prefs = prefs;
        migrateLegacySession();
    }

    // --- Lifecycle wiring ---------------------------------------------------

    /**
     * Restores the persisted window bounds onto the stage (before it is shown)
     * and starts tracking further changes into the snapshot.
     * <p>
     * The maximized flag is not taken from {@link Stage#maximizedProperty()} alone: on macOS,
     * un-zooming a window by dragging its edge or title only reports a plain resize / move, so the
     * property stays {@code true} for good and every later size would be lost. A window counts as
     * maximized only while it is flagged so <em>and</em> still fills its screen's visual bounds.
     */
    public void initWindow(Stage stage) {
        restoreWindowBounds(stage);
        stage.xProperty().subscribe(nv -> {
            winX = nv.doubleValue();
            updateMaximized(stage);
        });
        stage.yProperty().subscribe(nv -> {
            winY = nv.doubleValue();
            updateMaximized(stage);
        });
        stage.widthProperty().subscribe(nv -> {
            winW = nv.doubleValue();
            updateMaximized(stage);
        });
        stage.heightProperty().subscribe(nv -> {
            winH = nv.doubleValue();
            updateMaximized(stage);
        });
        stage.maximizedProperty().subscribe(nv -> updateMaximized(stage));
    }

    private void updateMaximized(Stage stage) {
        winMax = stage.isMaximized() && fillsScreen(stage);
    }

    /** Whether the stage covers (within a few pixels) the visual bounds of the screen it is on. */
    private static boolean fillsScreen(Stage stage) {
        double x = stage.getX();
        double y = stage.getY();
        double w = stage.getWidth();
        double h = stage.getHeight();
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h)) {
            return true; // not laid out yet: trust the flag
        }
        List<Screen> screens = Screen.getScreensForRectangle(x, y, w, h);
        if (screens.isEmpty()) {
            return true;
        }
        Rectangle2D visual = screens.getFirst().getVisualBounds();
        return w >= visual.getWidth() - MAXIMIZED_TOLERANCE
                && h >= visual.getHeight() - MAXIMIZED_TOLERANCE;
    }

    /**
     * Restores the persisted tool-bar arrangement onto the container and starts
     * tracking further user reorderings into the snapshot.
     */
    public void initToolbars(ToolBarContainer toolBars) {
        String stored = get(TOOLBARS, null);
        if (stored != null) {
            toolBars.applyArrangement(stored);
        }
        String hidden = get(TOOLBARS_HIDDEN, null);
        if (hidden != null) {
            toolBars.applyHiddenToolBarIds(hidden);
        }
        toolbarArrangement = toolBars.getArrangement();
        toolbarHidden = toolBars.getHiddenToolBarIds();
        toolBars.setOnArrangementChanged(() -> toolbarArrangement = toolBars.getArrangement());
        toolBars.setOnVisibilityChanged(() -> toolbarHidden = toolBars.getHiddenToolBarIds());
    }

    /**
     * Restores the persisted View settings into the shared {@link EditorSettings} (before editors
     * are opened, so they pick up the stored value) and starts tracking further changes into the
     * snapshot. A {@code null} settings instance (none registered) is ignored.
     */
    public void initViewSettings(EditorSettings settings) {
        if (settings == null) {
            return;
        }
        settings.showLineNumbers().set(getBoolean(VIEW_SHOW_LINE_NUMBERS, true));
        showLineNumbers = settings.showLineNumbers().get();
        settings.showLineNumbers().subscribe(nv -> showLineNumbers = nv);
        settings.showBreadcrumbs().set(getBoolean(VIEW_SHOW_BREADCRUMBS, true));
        showBreadcrumbs = settings.showBreadcrumbs().get();
        settings.showBreadcrumbs().subscribe(nv -> showBreadcrumbs = nv);
    }

    /**
     * Registers a JVM shutdown hook that persists the snapshot as a safety net
     * for exit paths that bypass the regular lifecycle.
     */
    public void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::save, "nbfx-state-save"));
    }

    /** Persists the current snapshot. Idempotent and safe to call off the FX thread. */
    public void save() {
        if (!saved.compareAndSet(false, true)) {
            return;
        }
        try {
            saveWindowBounds(winX, winY, winW, winH, winMax);
            if (toolbarArrangement != null) {
                prefs.put(TOOLBARS, toolbarArrangement);
            }
            if (toolbarHidden != null) {
                prefs.put(TOOLBARS_HIDDEN, toolbarHidden);
            }
            prefs.putBoolean(VIEW_SHOW_LINE_NUMBERS, showLineNumbers);
            prefs.putBoolean(VIEW_SHOW_BREADCRUMBS, showBreadcrumbs);
            flush();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Failed to persist application state", ex);
        }
    }

    // --- Main window bounds -------------------------------------------------

    /**
     * Applies the persisted window bounds (position, size, maximized) to the
     * given stage, falling back to the default size when nothing is stored
     * or the stored bounds are not visible on any current screen.
     */
    private void restoreWindowBounds(Stage stage) {
        double width = getDouble(WINDOW_WIDTH, DEFAULT_WIDTH);
        double height = getDouble(WINDOW_HEIGHT, DEFAULT_HEIGHT);
        double x = getDouble(WINDOW_X, Double.NaN);
        double y = getDouble(WINDOW_Y, Double.NaN);

        if (width <= 0 || height <= 0) {
            width = DEFAULT_WIDTH;
            height = DEFAULT_HEIGHT;
        }
        stage.setWidth(width);
        stage.setHeight(height);

        if (!Double.isNaN(x) && !Double.isNaN(y) && isVisibleOnAnyScreen(x, y, width, height)) {
            stage.setX(x);
            stage.setY(y);
        } else if (!Double.isNaN(x) || !Double.isNaN(y)) {
            LOG.log(Level.INFO, "Stored window position is off-screen; centering instead");
        }

        stage.setMaximized(prefs.getBoolean(WINDOW_MAXIMIZED, false));
    }

    /**
     * Persists the given window bounds. When maximized, only the flag is stored
     * so a later un-maximize keeps the previous restore-size.
     */
    private void saveWindowBounds(double x, double y, double width, double height, boolean maximized) {
        prefs.putBoolean(WINDOW_MAXIMIZED, maximized);
        if (!maximized) {
            prefs.putDouble(WINDOW_X, x);
            prefs.putDouble(WINDOW_Y, y);
            prefs.putDouble(WINDOW_WIDTH, width);
            prefs.putDouble(WINDOW_HEIGHT, height);
        }
        flush();
    }

    // --- Recent projects ----------------------------------------------------

    /** A recent project: its directory path and the resolved menu icon name (may be {@code null}). */
    public record RecentProject(String path, String iconName) {}

    /** The recent projects, most-recent first. */
    public List<RecentProject> getRecentProjects() {
        String raw = get(RECENT_PROJECTS, "");
        if (raw.isBlank()) {
            return List.of();
        }
        List<RecentProject> list = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab < 0) {
                list.add(new RecentProject(line, null));
            } else {
                String iconName = line.substring(tab + 1);
                list.add(new RecentProject(line.substring(0, tab), iconName.isBlank() ? null : iconName));
            }
        }
        return list;
    }

    /** The most recently opened project path, or {@code null} if none. */
    String getLastProject() {
        List<RecentProject> recent = getRecentProjects();
        return recent.isEmpty() ? null : recent.getFirst().path();
    }

    /**
     * The projects that were open when the application last exited, in the order they were opened.
     * Empty when none was open (or on first run).
     */
    public List<String> getOpenProjects() {
        String raw = get(PROJECT_OPEN, "");
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (!line.isBlank()) {
                paths.add(line);
            }
        }
        return List.copyOf(paths);
    }

    /** The path of the project that was selected when the application last exited, or {@code null}. */
    public String getSelectedProject() {
        String selected = get(PROJECT_SELECTED, "");
        return selected.isBlank() ? null : selected;
    }

    /**
     * Records the projects that are currently open and which one is selected, so the next launch
     * reopens exactly this session. Persisted immediately (rather than only at exit) so that a
     * project the user closed is not brought back by a crash.
     */
    public void setOpenProjects(List<String> paths, String selected) {
        List<String> kept = paths == null ? List.of()
                : paths.stream().filter(path -> path != null && !path.isBlank()).toList();
        if (kept.isEmpty()) {
            prefs.remove(PROJECT_OPEN);
        } else {
            prefs.put(PROJECT_OPEN, String.join("\n", kept));
        }
        if (selected == null || selected.isBlank() || !kept.contains(selected)) {
            prefs.remove(PROJECT_SELECTED);
        } else {
            prefs.put(PROJECT_SELECTED, selected);
        }
        flush();
    }

    /**
     * Removes the persisted per-project state (tree expansion and selection) of the given project,
     * so a subsequent open starts fresh at the project root. The session layout is shared by every
     * project and is left alone. A {@code null} project is ignored.
     */
    public void clearProjectState(File project) {
        if (project == null) {
            return;
        }
        String key = expansionKey(project.getPath());
        prefs.node(TREE_EXPANDED_NODE).remove(key);
        prefs.node(TREE_SELECTED_NODE).remove(key);
        flush();
    }

    /** Records a project as the most recent, de-duplicating and bounding the list. */
    public void addRecentProject(String path, String iconName) {
        if (path == null || path.isBlank()) {
            return;
        }
        List<RecentProject> list = new ArrayList<>(getRecentProjects());
        list.removeIf(rp -> rp.path().equals(path));
        list.addFirst(new RecentProject(path, iconName));
        while (list.size() > MAX_RECENT_PROJECTS) {
            list.removeLast();
        }
        prefs.put(RECENT_PROJECTS, serialize(list));
        flush();
    }

    /** Removes a project from the recent list (e.g. when its directory no longer exists). */
    public void removeRecentProject(String path) {
        List<RecentProject> list = new ArrayList<>(getRecentProjects());
        if (list.removeIf(rp -> rp.path().equals(path))) {
            prefs.put(RECENT_PROJECTS, serialize(list));
            flush();
        }
    }

    public void clearRecentProjects() {
        prefs.remove(RECENT_PROJECTS);
        flush();
    }

    // --- Project tree expansion --------------------------------------------

    /**
     * The persisted expanded-node identifiers for {@code projectPath}, or empty if none.
     * Expansion is stored per project in a dedicated preferences child node, keyed by a stable
     * hash of the project path.
     */
    public List<String> getExpandedNodes(String projectPath) {
        String[] lines = projectLines(TREE_EXPANDED_NODE, projectPath, 1);
        List<String> list = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                list.add(lines[i]);
            }
        }
        return list;
    }

    /**
     * Reads the per-project payload stored under {@code childNode}, split into lines. The project
     * path is embedded as the first line and re-checked here, guarding against collisions of the
     * hashed {@link #expansionKey}. Returns an empty array when the entry is absent, too short, or
     * belongs to another project.
     */
    private String[] projectLines(String childNode, String projectPath, int minLines) {
        if (projectPath == null) {
            return EMPTY_LINES;
        }
        String raw = prefs.node(childNode).get(expansionKey(projectPath), "");
        if (raw.isBlank()) {
            return EMPTY_LINES;
        }
        String[] lines = raw.split("\n", -1);
        if (lines.length < minLines || !projectPath.equals(lines[0])) {
            return EMPTY_LINES;
        }
        return lines;
    }

    /** Persists the expanded-node identifiers for {@code projectPath}. */
    void setExpandedNodes(String projectPath, List<String> nodes) {
        if (projectPath == null) {
            return;
        }
        Preferences node = prefs.node(TREE_EXPANDED_NODE);
        String key = expansionKey(projectPath);
        if (nodes == null || nodes.isEmpty()) {
            node.remove(key);
        } else {
            node.put(key, projectPath + "\n" + String.join("\n", nodes));
        }
        flush();
    }

    /** The persisted selected-node identifier for {@code projectPath}, or {@code null} if none. */
    public String getSelectedNode(String projectPath) {
        String[] lines = projectLines(TREE_SELECTED_NODE, projectPath, 2);
        if (lines.length < 2 || lines[1].isBlank()) {
            return null;
        }
        return lines[1];
    }

    /** Persists the selected-node identifier for {@code projectPath}. */
    void setSelectedNode(String projectPath, String nodeId) {
        if (projectPath == null) {
            return;
        }
        Preferences node = prefs.node(TREE_SELECTED_NODE);
        String key = expansionKey(projectPath);
        if (nodeId == null || nodeId.isBlank()) {
            node.remove(key);
        } else {
            node.put(key, projectPath + "\n" + nodeId);
        }
        flush();
    }

    // --- helpers ------------------------------------------------------------

    private double getDouble(String key, double def) {
        try {
            return prefs.getDouble(key, def);
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private String get(String key, String def) {
        try {
            return prefs.get(key, def);
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private boolean getBoolean(String key, boolean def) {
        try {
            return prefs.getBoolean(key, def);
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private static boolean isVisibleOnAnyScreen(double x, double y, double w, double h) {
        Rectangle2D window = new Rectangle2D(x, y, Math.max(1, w), Math.max(1, h));
        for (Screen screen : Screen.getScreens()) {
            if (screen.getVisualBounds().intersects(window)) {
                return true;
            }
        }
        return false;
    }

    private void flush() {
        try {
            prefs.flush();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Could not flush preferences", ex);
        }
    }

    private static String serialize(List<RecentProject> list) {
        StringBuilder sb = new StringBuilder();
        for (RecentProject rp : list) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(rp.path());
            if (rp.iconName() != null && !rp.iconName().isBlank()) {
                sb.append('\t').append(rp.iconName());
            }
        }
        return sb.toString();
    }

    private static String expansionKey(String projectPath) {
        return Integer.toHexString(projectPath.hashCode());
    }

    // --- Window layout (panes + their tabs) ---------------------------------

    /** The kind of content a persisted tab holds. */
    public enum TabKind { EDITOR, VIEW }

    /**
     * A persisted tab. For {@link TabKind#EDITOR} the {@code id} is an absolute file path and the
     * scroll/caret fields are meaningful; for {@link TabKind#VIEW} the {@code id} is the view
     * provider identifier and the remaining fields are unused.
     */
    public record TabEntry(TabKind kind, String id, int topParagraph, int caretParagraph, int caretColumn,
                           SplitEntry split) {

        /** An entry without a split. */
        public TabEntry(TabKind kind, String id, int topParagraph, int caretParagraph, int caretColumn) {
            this(kind, id, topParagraph, caretParagraph, caretColumn, null);
        }

        static TabEntry view(String providerId) {
            return new TabEntry(TabKind.VIEW, providerId, 0, 0, 0);
        }
    }

    /**
     * The split of a persisted editor tab (see {@link com.gluonhq.netbeans.nbfx.launcher.ui.EditorSplit}):
     * its orientation and the scroll/caret of the editor in its second half.
     */
    public record SplitEntry(Orientation orientation, int topParagraph, int caretParagraph, int caretColumn) {
    }

    /**
     * A persisted pane: its {@link com.gluonhq.netbeans.nbfx.launcher.ui.NbfxTabPane.PaneRole role},
     * its bounds (only meaningful, and only valid, for a detached pane), its tabs in order and the
     * index of the selected one ({@code -1} when the pane is empty).
     * <p>
     * Tabs of both kinds may appear in any pane, since the user can drag them freely between panes.
     */
    public record PaneLayout(NbfxTabPane.PaneRole role, double x, double y, double width, double height,
                      List<TabEntry> tabs, int activeIndex) {

        static PaneLayout docked(NbfxTabPane.PaneRole role, List<TabEntry> tabs, int activeIndex) {
            return new PaneLayout(role, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    tabs, activeIndex);
        }

        /** This pane with its editor tabs removed, and {@code activeIndex} adjusted accordingly. */
        PaneLayout withoutEditors() {
            List<TabEntry> kept = new ArrayList<>();
            int active = -1;
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).kind() == TabKind.VIEW) {
                    if (i == activeIndex) {
                        active = kept.size();
                    }
                    kept.add(tabs.get(i));
                }
            }
            return new PaneLayout(role, x, y, width, height, kept,
                    active >= 0 ? active : (kept.isEmpty() ? -1 : 0));
        }
    }

    /**
     * The full window layout: every pane - the dock area's in the depth-first order of its
     * {@link DockTree}, then any permanent pane hidden for being empty, then the detached panes -
     * the index into {@code panes} of the pane that held focus ({@code -1} when unknown), and the
     * shape of the dock area ({@code null} when unknown, in which case the docked panes are stacked).
     * The tree's leaves are matched to the docked panes by {@link #leafPanes(DockTree)}.
     */
    public record Layout(List<PaneLayout> panes, int focusedIndex, DockTree dock) {

        static final Layout EMPTY = new Layout(List.of(), -1);

        public Layout {
            panes = List.copyOf(panes);
        }

        public Layout(List<PaneLayout> panes, int focusedIndex) {
            this(panes, focusedIndex, null);
        }

        Layout(List<PaneLayout> panes) {
            this(panes, -1);
        }

        /** The panes of the dock area (not detached), in the order they were persisted. */
        public List<PaneLayout> dockedPanes() {
            return panes.stream().filter(pane -> pane.role() != NbfxTabPane.PaneRole.DETACHED).toList();
        }

        /**
         * The pane each leaf of {@code tree} stands for, in the tree's depth-first order: a permanent
         * leaf's pane is the one playing its role, the docked leaves take the {@code DOCKED} panes in
         * order. Returns {@code null} when the tree does not fit these panes: a permanent leaf with no
         * pane, or a different number of docked leaves and panes.
         */
        public List<PaneLayout> leafPanes(DockTree tree) {
            List<PaneLayout> docked = dockedPanes();
            List<PaneLayout> spare = new ArrayList<>(
                    docked.stream().filter(pane -> pane.role() == NbfxTabPane.PaneRole.DOCKED).toList());
            List<PaneLayout> result = new ArrayList<>();
            for (DockTree.Leaf leaf : tree.leaves()) {
                PaneLayout pane;
                if (leaf.isPrimary()) {
                    pane = docked.stream().filter(p -> p.role().name().equals(leaf.primary())).findFirst().orElse(null);
                } else {
                    pane = spare.isEmpty() ? null : spare.removeFirst();
                }
                if (pane == null) {
                    return null;
                }
                result.add(pane);
            }
            return spare.isEmpty() ? result : null;
        }

        public boolean isEmpty() {
            return panes.isEmpty();
        }

        /** The pane whose window had focus, or {@code null} when that is unknown. */
        PaneLayout focusedPane() {
            return focusedIndex >= 0 && focusedIndex < panes.size() ? panes.get(focusedIndex) : null;
        }

        /**
         * This layout reduced to what can be applied before any project has loaded: editor tabs
         * (whose files belong to the projects being reopened) are dropped, and detached or bottom
         * panes left without any tab are dropped with them.
         */
        Layout withoutEditors() {
            List<PaneLayout> kept = new ArrayList<>();
            Set<PaneLayout> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
            int focused = -1;
            for (int i = 0; i < panes.size(); i++) {
                PaneLayout stripped = panes.get(i).withoutEditors();
                if (stripped.tabs().isEmpty() && !stripped.role().isPermanent()) {
                    dropped.add(panes.get(i));
                    continue;
                }
                // Dropping panes shifts the positions the focus index refers to.
                if (i == focusedIndex) {
                    focused = kept.size();
                }
                kept.add(stripped);
            }
            // A dropped docked pane leaves the area's tree as well.
            DockTree pruned = null;
            List<PaneLayout> leafPanes = dock == null ? null : leafPanes(dock);
            if (leafPanes != null) {
                pruned = dock.retainLeaves(i -> !dropped.contains(leafPanes.get(i)));
            }
            return new Layout(kept, focused, pruned);
        }
    }

    /**
     * The layout of the whole session - every pane, its tabs and the windows they live in, across
     * all the projects that were open - or {@link Layout#EMPTY} when none is stored.
     */
    Layout getSessionLayout() {
        return readLayout(LAYOUT_SESSION_KEY, "");
    }

    /** Persists the layout of the whole session. */
    void setSessionLayout(Layout layout) {
        writeLayout(LAYOUT_SESSION_KEY, "", layout);
    }

    private Layout readLayout(String key, String guard) {
        String raw = prefs.node(LAYOUT_NODE).get(key, "");
        if (raw.isBlank()) {
            return Layout.EMPTY;
        }
        String[] lines = raw.split("\n", -1);
        if (lines.length < 2) {
            return Layout.EMPTY;
        }
        // Line 0 is the guard (empty for the session layout, the project path for a legacy
        // per-project one) followed by the index of the focused pane and the tree of the dock area.
        // Layouts written before the focus index carry the guard alone; those written before docking
        // carry no tree, and the two per-column trees of an early docking version fail to parse.
        String[] header = lines[0].split(FIELD_SEP, -1);
        if (!guard.equals(header[0])) {
            return Layout.EMPTY;
        }
        List<PaneLayout> panes = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            PaneLayout pane = parsePane(lines[i]);
            if (pane != null) {
                panes.add(pane);
            }
        }
        if (panes.isEmpty()) {
            return Layout.EMPTY;
        }
        int focused = header.length > 1 ? Math.min(parseInt(header, 1), panes.size() - 1) : -1;
        DockTree dock = header.length > 2 ? DockTrees.parse(header[2]) : null;
        return new Layout(panes, Math.max(focused, -1), dock);
    }

    private void writeLayout(String key, String guard, Layout layout) {
        Preferences node = prefs.node(LAYOUT_NODE);
        if (layout == null || layout.isEmpty()) {
            node.remove(key);
        } else {
            StringBuilder sb = new StringBuilder(guard)
                    .append(FIELD_SEP).append(layout.focusedIndex())
                    .append(FIELD_SEP).append(layout.dock() == null ? "" : DockTrees.format(layout.dock()));
            for (PaneLayout pane : layout.panes()) {
                sb.append('\n').append(serialize(pane));
            }
            node.put(key, sb.toString());
        }
        flush();
    }

    // --- Migration from the single-project session -------------------------

    /**
     * Converts the state written by the single-project versions into the session format, once:
     * the boolean {@code project.open} becomes the list of open projects, and the layout of the
     * last project (or, failing that, the project-independent one) becomes the session layout.
     * Every legacy per-project layout is then dropped. A userdir with no legacy state, or one
     * already carrying a session layout, is left untouched.
     */
    private void migrateLegacySession() {
        try {
            Preferences layoutNode = prefs.node(LAYOUT_NODE);
            String legacyOpen = prefs.get(PROJECT_OPEN, null);
            boolean legacyFlag = "true".equals(legacyOpen) || "false".equals(legacyOpen);
            if (!layoutNode.get(LAYOUT_SESSION_KEY, "").isBlank()
                    || (!legacyFlag && layoutNode.keys().length == 0)) {
                return;
            }
            String last = getLastProject();
            Layout layout = last == null ? Layout.EMPTY : readLayout(expansionKey(last), last);
            if (layout.isEmpty()) {
                layout = readLayout(LAYOUT_LEGACY_GLOBAL_KEY, "");
            }
            for (String key : layoutNode.keys()) {
                layoutNode.remove(key);
            }
            setSessionLayout(layout);
            // A missing flag meant "open": that was its default.
            boolean wasOpen = !"false".equals(legacyOpen);
          setOpenProjects(wasOpen && last != null ? List.of(last) : List.of(), last);
            LOG.info(() -> "Migrated the legacy session state" + (last == null ? "" : " of " + last));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Could not migrate the legacy session state", ex);
        }
    }

    private static String serialize(PaneLayout pane) {
        StringBuilder sb = new StringBuilder(pane.role().name())
                .append(FIELD_SEP).append(pane.x())
                .append(FIELD_SEP).append(pane.y())
                .append(FIELD_SEP).append(pane.width())
                .append(FIELD_SEP).append(pane.height())
                .append(FIELD_SEP).append(pane.activeIndex());
        for (TabEntry tab : pane.tabs()) {
            sb.append(FIELD_SEP).append(tab.kind().name())
                    .append(TAB_SEP).append(tab.id())
                    .append(TAB_SEP).append(tab.topParagraph())
                    .append(TAB_SEP).append(tab.caretParagraph())
                    .append(TAB_SEP).append(tab.caretColumn());
            if (tab.split() != null) {
                sb.append(TAB_SEP).append(tab.split().orientation().name())
                        .append(TAB_SEP).append(tab.split().topParagraph())
                        .append(TAB_SEP).append(tab.split().caretParagraph())
                        .append(TAB_SEP).append(tab.split().caretColumn());
            }
        }
        return sb.toString();
    }

    /** Parses one serialized pane line, returning {@code null} if it is malformed or unknown. */
    private static PaneLayout parsePane(String line) {
        if (line.isBlank()) {
            return null;
        }
        String[] f = line.split(FIELD_SEP, -1);
        if (f.length < 6) {
            return null;
        }
        NbfxTabPane.PaneRole role = parseEnum(NbfxTabPane.PaneRole.class, f[0]);
        if (role == null) {
            return null;
        }
        List<TabEntry> tabs = new ArrayList<>();
        for (int i = 6; i < f.length; i++) {
            TabEntry tab = parseTab(f[i]);
            if (tab != null) {
                tabs.add(tab);
            }
        }
        int active = Math.min(parseInt(f, 5), tabs.size() - 1);
        if (tabs.isEmpty() && !role.isPermanent()) {
            // A detached or docked pane exists only for its tabs; without any it would restore as an
            // empty window or an empty split.
            return null;
        }
        return new PaneLayout(role, parseDouble(f, 1), parseDouble(f, 2),
                parseDouble(f, 3), parseDouble(f, 4), tabs, active);
    }

    /** Parses one serialized tab field, returning {@code null} if it is malformed or unknown. */
    private static TabEntry parseTab(String field) {
        if (field.isBlank()) {
            return null;
        }
        String[] p = field.split(TAB_SEP, -1);
        // Navigator tabs were persisted as NAVIGATOR before views generalised them.
        TabKind kind = p.length < 2 ? null
                : "NAVIGATOR".equals(p[0].trim()) ? TabKind.VIEW : parseEnum(TabKind.class, p[0]);
        if (kind == null || p[1].isBlank()) {
            return null;
        }
        Orientation orientation = p.length > 5 ? parseEnum(Orientation.class, p[5]) : null;
        SplitEntry split = orientation == null ? null
                : new SplitEntry(orientation, parseInt(p, 6), parseInt(p, 7), parseInt(p, 8));
        return new TabEntry(kind, p[1], parseInt(p, 2), parseInt(p, 3), parseInt(p, 4), split);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int parseInt(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double parseDouble(String[] parts, int index) {
        if (index >= parts.length) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(parts[index].trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    // --- Detached-window geometry ------------------------------------------

    /** Returns valid {@code {x, y, width, height}}, centering on the primary screen if off-screen. */
    static double[] clampBounds(double x, double y, double width, double height) {
        double w = width > 0 ? width : DEFAULT_DETACHED_WIDTH;
        double h = height > 0 ? height : DEFAULT_DETACHED_HEIGHT;
        if (Double.isNaN(x) || Double.isNaN(y) || !isVisibleOnAnyScreen(x, y, w, h)) {
            Rectangle2D vb = Screen.getPrimary().getVisualBounds();
            x = vb.getMinX() + (vb.getWidth() - w) / 2;
            y = vb.getMinY() + (vb.getHeight() - h) / 2;
        }
        return new double[]{x, y, w, h};
    }

}

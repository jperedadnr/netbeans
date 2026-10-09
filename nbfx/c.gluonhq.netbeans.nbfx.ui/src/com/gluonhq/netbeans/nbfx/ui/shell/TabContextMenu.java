package com.gluonhq.netbeans.nbfx.ui.shell;

import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.docking.DockArea;
import com.gluonhq.netbeans.nbfx.docking.DropTarget;
import com.gluonhq.netbeans.nbfx.file.actions.DesktopActions;
import com.gluonhq.netbeans.nbfx.ui.session.DocumentCloser;
import com.gluonhq.netbeans.nbfx.ui.shell.NbfxTabPane.PaneRole;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The context menu of a tab header, as in NetBeans, in two flavours.
 * <p>
 * <b>View tabs</b> (Projects, Files): closing the tab or its whole pane (the <em>group</em>);
 * floating them into a new window and docking them back home; moving them by keyboard (see
 * {@link TabMover}); shifting the tab among its neighbours.
 * <p>
 * <b>Editor tabs</b> add closing all / the other tabs of the pane, moving the tab into a new
 * <em>document tab group</em> (pane) or collapsing the pane into another, and file actions:
 * revealing the file in the Projects view, copying its path, opening it with the system application;
 * and splitting the tab in two editors of the same file (see {@link EditorSplit}).
 * <p>
 * The items that only make sense in the main window (Float) or in a detached one (Dock) are disabled
 * elsewhere; the state of every item is refreshed each time the menu opens, as the tab may have been
 * dragged anywhere in between. Maximize / Minimize / Size Group are placeholders for now.
 * <p>
 * The same actions are offered from the Window menu's <em>Configure Window</em> submenu (see
 * {@link #createConfigureWindowMenu()}), applied to the tab that holds the focus - the editor-only
 * items (Clone, Split, tab groups) are disabled while a view tab is the active one.
 */
public final class TabContextMenu {

    private TabContextMenu() {}

    /** Gives the view {@code tab} (Projects, Files, ...) its context menu. */
    public static void installForView(Tab tab) {
        ContextMenu menu = new ContextMenu();

        MenuItem close = item("close", e -> close(tab));
        close.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
        MenuItem closeGroup = item("closeGroup", e -> closeGroup(tab.getTabPane()));

        MenuItem maximize = todo("maximize");
        MenuItem minimize = todo("minimize");
        MenuItem minimizeGroup = todo("minimizeGroup");
        MenuItem floatTab = item("float", e -> floatTab(tab));
        MenuItem floatGroup = item("floatGroup", e -> floatGroup(tab.getTabPane()));
        MenuItem dock = item("dock", e -> dock(tab));
        MenuItem dockGroup = item("dockGroup", e -> dockGroup(tab.getTabPane()));

        MenuItem move = item("move", e -> TabMover.moveTab(tab));
        MenuItem moveGroup = item("moveGroup", e -> TabMover.movePane(tab.getTabPane()));
        MenuItem shiftLeft = item("shiftLeft", e -> shift(tab, -1));
        MenuItem shiftRight = item("shiftRight", e -> shift(tab, 1));
        MenuItem sizeGroup = todo("sizeGroup");

        menu.getItems().addAll(close, closeGroup, new SeparatorMenuItem(),
                maximize, minimize, minimizeGroup, floatTab, floatGroup, dock, dockGroup, new SeparatorMenuItem(),
                move, moveGroup, shiftLeft, shiftRight, sizeGroup);

        menu.setOnShowing(e -> refreshCommon(tab, floatTab, floatGroup, dock, dockGroup,
                move, moveGroup, shiftLeft, shiftRight));
        tab.setContextMenu(menu);
    }

    /** Gives the editor {@code tab} its context menu. */
    public static void installForEditor(Tab tab) {
        ContextMenu menu = new ContextMenu();

        MenuItem close = item("close", e -> close(tab));
        close.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
        MenuItem closeAll = item("closeAll", e -> closeGroup(tab.getTabPane()));
        closeAll.setAccelerator(new KeyCodeCombination(KeyCode.W,
                KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        MenuItem closeOther = item("closeOther", e -> closeOthers(tab));

        MenuItem maximize = todo("maximize");
        MenuItem floatTab = item("float", e -> floatTab(tab));
        MenuItem floatAll = item("floatAll", e -> floatGroup(tab.getTabPane()));
        MenuItem dock = item("dock", e -> dock(tab));
        MenuItem dockAll = item("dockAll", e -> dockGroup(tab.getTabPane()));

        MenuItem move = item("move", e -> TabMover.moveTab(tab));
        MenuItem moveAll = item("moveAll", e -> TabMover.movePane(tab.getTabPane()));
        MenuItem shiftLeft = item("shiftLeft", e -> shift(tab, -1));
        MenuItem shiftRight = item("shiftRight", e -> shift(tab, 1));

        MenuItem clone = item("clone", e -> cloneDocument(tab));
        MenuItem newGroup = item("newGroup", e -> newGroup(tab));
        MenuItem collapseGroup = item("collapseGroup", e -> collapseGroup(tab.getTabPane()));

        MenuItem selectInProjects = item("selectInProjects", e -> NbfxTabPane.revealInNavigator(fileOf(tab), 0));
        MenuItem copyPath = item("copyPath", e -> copyPath(fileOf(tab)));
        MenuItem openInSystem = item("openInSystem", e -> DesktopActions.openInSystem(toFile(fileOf(tab))));

        Menu split = new Menu(message("split"));
        MenuItem splitVertically = item("splitVertically", e -> EditorSplit.split(tab, Orientation.VERTICAL));
        MenuItem splitHorizontally = item("splitHorizontally", e -> EditorSplit.split(tab, Orientation.HORIZONTAL));
        MenuItem splitClear = item("splitClear", e -> EditorSplit.clear(tab));
        split.getItems().addAll(splitVertically, splitHorizontally, splitClear);

        menu.getItems().addAll(close, closeAll, closeOther, new SeparatorMenuItem(),
                maximize, floatTab, floatAll, dock, dockAll, new SeparatorMenuItem(),
                move, moveAll, shiftLeft, shiftRight, new SeparatorMenuItem(),
                clone, newGroup, collapseGroup, new SeparatorMenuItem(),
                selectInProjects, copyPath, openInSystem, new SeparatorMenuItem(),
                split);

        menu.setOnShowing(e -> {
            refreshCommon(tab, floatTab, floatAll, dock, dockAll, move, moveAll, shiftLeft, shiftRight);
            TabPane pane = tab.getTabPane();
            int count = pane == null ? 0 : pane.getTabs().size();
            boolean inArea = inArea(pane);
            closeOther.setDisable(count <= 1);
            newGroup.setDisable(!inArea || count <= 1);
            collapseGroup.setDisable(!inArea || collapseTargetOf(pane) == null);
            File file = toFile(fileOf(tab));
            clone.setDisable(NbfxTabPane.documentOf(tab) == null);
            selectInProjects.setDisable(file == null);
            copyPath.setDisable(file == null);
            openInSystem.setDisable(file == null);
            Orientation current = EditorSplit.orientationOf(tab);
            splitVertically.setDisable(!EditorSplit.canSplit(tab) || current == Orientation.VERTICAL);
            splitHorizontally.setDisable(!EditorSplit.canSplit(tab) || current == Orientation.HORIZONTAL);
            splitClear.setDisable(current == null);
        });
        tab.setContextMenu(menu);
    }

    /**
     * Builds the Window ▸ Configure Window submenu: the layout actions of the tab context menus, applied
     * to the tab that holds the keyboard focus ({@link NbfxTabPane#focusedPane()}'s selected tab) when
     * the menu opens. The enablement is refreshed each time, and every item is disabled when no tab
     * has the focus.
     */
    public static Menu createConfigureWindowMenu() {
        Menu menu = new Menu(message("configureWindow"));

        MenuItem maximize = todo("maximize");
        MenuItem floatTab = item("float", e -> withFocusedTab(TabContextMenu::floatTab));
        MenuItem floatGroup = item("floatGroup", e -> withFocusedTab(tab -> floatGroup(tab.getTabPane())));
        MenuItem minimize = todo("minimize");
        MenuItem minimizeGroup = todo("minimizeGroup");
        MenuItem dock = item("dock", e -> withFocusedTab(TabContextMenu::dock));
        MenuItem dockGroup = item("dockGroup", e -> withFocusedTab(tab -> dockGroup(tab.getTabPane())));

        MenuItem clone = item("cloneDocument", e -> withFocusedTab(TabContextMenu::cloneDocument));
        Menu split = new Menu(message("splitDocument"));
        MenuItem splitVertically = item("splitVertically",
                e -> withFocusedTab(tab -> EditorSplit.split(tab, Orientation.VERTICAL)));
        MenuItem splitHorizontally = item("splitHorizontally",
                e -> withFocusedTab(tab -> EditorSplit.split(tab, Orientation.HORIZONTAL)));
        MenuItem splitClear = item("splitClear", e -> withFocusedTab(EditorSplit::clear));
        split.getItems().addAll(splitVertically, splitHorizontally, splitClear);
        MenuItem newGroup = item("newGroup", e -> withFocusedTab(TabContextMenu::newGroup));
        MenuItem collapseGroup = item("collapseGroup", e -> withFocusedTab(tab -> collapseGroup(tab.getTabPane())));

        menu.getItems().addAll(maximize, floatTab, floatGroup, minimize, minimizeGroup, dock, dockGroup,
                new SeparatorMenuItem(), clone, split, newGroup, collapseGroup);

        menu.setOnShowing(e -> {
            Tab tab = focusedTab();
            TabPane pane = tab == null ? null : tab.getTabPane();
            boolean detached = pane != null && NbfxTabPane.roleOf(pane) == PaneRole.DETACHED;
            boolean inArea = inArea(pane);
            boolean editor = tab != null && NbfxTabPane.documentOf(tab) != null;
            int count = pane == null ? 0 : pane.getTabs().size();
            floatTab.setDisable(tab == null || detached);
            floatGroup.setDisable(tab == null || detached);
            dock.setDisable(!detached || NbfxTabPane.homeOf(tab) == null);
            dockGroup.setDisable(!detached);
            clone.setDisable(!editor);
            Orientation current = tab == null ? null : EditorSplit.orientationOf(tab);
            boolean canSplit = editor && EditorSplit.canSplit(tab);
            splitVertically.setDisable(!canSplit || current == Orientation.VERTICAL);
            splitHorizontally.setDisable(!canSplit || current == Orientation.HORIZONTAL);
            splitClear.setDisable(current == null);
            split.setDisable(!editor);
            newGroup.setDisable(!editor || !inArea || count <= 1);
            collapseGroup.setDisable(!editor || !inArea || collapseTargetOf(pane) == null);
        });
        return menu;
    }

    /** The selected tab of the pane that holds the focus, or {@code null} when there is none. */
    private static Tab focusedTab() {
        TabPane pane = NbfxTabPane.focusedPane();
        return pane == null ? null : pane.getSelectionModel().getSelectedItem();
    }

    private static void withFocusedTab(Consumer<Tab> action) {
        Tab tab = focusedTab();
        if (tab != null) {
            action.accept(tab);
        }
    }

    /** Refreshes the enabled state of the items shared by both menus. */
    private static void refreshCommon(Tab tab, MenuItem floatTab, MenuItem floatGroup, MenuItem dock,
            MenuItem dockGroup, MenuItem move, MenuItem moveGroup, MenuItem shiftLeft, MenuItem shiftRight) {
        TabPane pane = tab.getTabPane();
        boolean detached = pane != null && NbfxTabPane.roleOf(pane) == PaneRole.DETACHED;
        boolean inArea = inArea(pane);
        int index = pane == null ? -1 : pane.getTabs().indexOf(tab);
        floatTab.setDisable(detached);
        floatGroup.setDisable(detached);
        dock.setDisable(!detached || NbfxTabPane.homeOf(tab) == null);
        dockGroup.setDisable(!detached);
        move.setDisable(Docking.area() == null || (!inArea && !detached));
        moveGroup.setDisable(Docking.area() == null || (!inArea && !detached));
        shiftLeft.setDisable(index <= 0);
        shiftRight.setDisable(pane == null || index >= pane.getTabs().size() - 1);
    }

    private static boolean inArea(TabPane pane) {
        return pane != null && Docking.area() != null && Docking.area().contains(pane);
    }

    private static String message(String key) {
        return NbBundle.getMessage(TabContextMenu.class, "TabMenu." + key);
    }

    private static MenuItem item(String key, EventHandler<ActionEvent> action) {
        MenuItem item = new MenuItem(message(key));
        item.setOnAction(action);
        return item;
    }

    private static MenuItem todo(String key) {
        MenuItem item = new MenuItem(message(key));
        item.setDisable(true);
        return item;
    }

    private static FileObject fileOf(Tab tab) {
        return tab.getUserData() instanceof FileObject fo ? fo : null;
    }

    private static File toFile(FileObject file) {
        return file == null ? null : FileUtil.toFile(file);
    }

    // --- Close -----------------------------------------------------------------------------------

    /** Closes {@code tab}: an editor through the document closer (confirming unsaved changes), a view directly. */
    private static void close(Tab tab) {
        EditorDocument document = NbfxTabPane.documentOf(tab);
        if (document != null) {
            DocumentCloser.closeDocument(document);
        } else {
            NbfxTabPane.closeTab(tab);
        }
    }

    /** Closes every tab of {@code pane}, confirming the unsaved editors once; an emptied pane goes away by itself. */
    private static void closeGroup(TabPane pane) {
        if (pane != null) {
            closeTabs(pane, List.copyOf(pane.getTabs()));
        }
    }

    /** Closes every tab of {@code tab}'s pane but {@code tab} itself. */
    private static void closeOthers(Tab tab) {
        TabPane pane = tab.getTabPane();
        if (pane == null) {
            return;
        }
        List<Tab> others = new ArrayList<>(pane.getTabs());
        others.remove(tab);
        closeTabs(pane, others);
    }

    /** Closes {@code tabs} of {@code pane}: the editors first, all confirmed at once, then the views unless cancelled. */
    private static void closeTabs(TabPane pane, List<Tab> tabs) {
        List<EditorDocument> documents = new ArrayList<>();
        List<Tab> views = new ArrayList<>();
        for (Tab tab : tabs) {
            EditorDocument document = NbfxTabPane.documentOf(tab);
            if (document != null) {
                documents.add(document);
            } else {
                views.add(tab);
            }
        }
        if (DocumentCloser.closeDocuments(documents)) {
            views.forEach(NbfxTabPane::closeTab);
        }
    }

    // --- Float / Dock ----------------------------------------------------------------------------

    private static void floatTab(Tab tab) {
        Point2D at = screenLocationOf(tab);
        if (at != null) {
            NbfxTabPane.detachTabToWindowAt(tab, at.getX(), at.getY());
        }
    }

    private static void floatGroup(TabPane pane) {
        if (pane == null || pane.getTabs().isEmpty()) {
            return;
        }
        Bounds b = pane.localToScreen(pane.getBoundsInLocal());
        Tab selected = pane.getSelectionModel().getSelectedItem();
        NbfxTabPane.openDetachedWindow(List.copyOf(pane.getTabs()), selected,
                b.getMinX(), b.getMinY(), Math.max(b.getWidth(), 300), Math.max(b.getHeight(), 200));
    }

    private static void dock(Tab tab) {
        TabPane home = NbfxTabPane.homeOf(tab);
        if (home != null) {
            NbfxTabPane.moveTab(home, tab, home.getTabs().size());
            NbfxTabPane.selectTabAndMoveToFront(tab);
        }
    }

    private static void dockGroup(TabPane pane) {
        if (pane == null) {
            return;
        }
        Tab selected = pane.getSelectionModel().getSelectedItem();
        for (Tab tab : List.copyOf(pane.getTabs())) {
            TabPane home = NbfxTabPane.homeOf(tab);
            if (home != null) {
                NbfxTabPane.moveTab(home, tab, home.getTabs().size());
            }
        }
        if (selected != null && selected.getTabPane() != pane) {
            NbfxTabPane.selectTabAndMoveToFront(selected);
        }
    }

    // --- Document tab groups ---------------------------------------------------------------------

    /** Moves {@code tab} into a new pane split off to the right of its current one. */
    private static void newGroup(Tab tab) {
        TabPane pane = tab.getTabPane();
        DockArea<TabPane> area = Docking.area();
        if (pane == null || area == null || !area.contains(pane) || pane.getTabs().size() <= 1) {
            return;
        }
        TabPane target = area.split(new DropTarget<>(pane, Side.RIGHT));
        NbfxTabPane.moveTab(target, tab, 0);
        NbfxTabPane.selectTabAndMoveToFront(tab);
    }

    /** Moves every tab of {@code pane} into another pane of the area; the emptied pane goes away by itself. */
    private static void collapseGroup(TabPane pane) {
        TabPane target = collapseTargetOf(pane);
        if (target == null) {
            return;
        }
        Tab selected = pane.getSelectionModel().getSelectedItem();
        for (Tab tab : List.copyOf(pane.getTabs())) {
            NbfxTabPane.moveTab(target, tab, target.getTabs().size());
        }
        if (selected != null) {
            NbfxTabPane.selectTabAndMoveToFront(selected);
        }
    }

    /**
     * The pane {@code pane} collapses into: the main pane when it is showing, otherwise another
     * visible pane of the area that already holds editors. {@code null} when there is none - a
     * document group never collapses into a pane of views only, such as the navigator.
     */
    private static TabPane collapseTargetOf(TabPane pane) {
        DockArea<TabPane> area = Docking.area();
        if (pane == null || area == null || !area.contains(pane)) {
            return null;
        }
        List<TabPane> leaves = area.leaves();
        TabPane main = area.mainPane();
        if (main != null && main != pane && leaves.contains(main)) {
            return main;
        }
        for (TabPane leaf : leaves) {
            if (leaf != pane && !NbfxTabPane.documentsOf(leaf).isEmpty()) {
                return leaf;
            }
        }
        return null;
    }

    // --- File ------------------------------------------------------------------------------------

    /** Opens a clone of {@code tab}'s document next to it (see {@link ContentManager#cloneDocument}). */
    private static void cloneDocument(Tab tab) {
        EditorDocument document = NbfxTabPane.documentOf(tab);
        ContentManager cm = Lookup.getDefault().lookup(ContentManager.class);
        if (document != null && cm != null) {
            cm.cloneDocument(document);
        }
    }

    private static void copyPath(FileObject file) {
        File f = toFile(file);
        if (f != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(f.getAbsolutePath());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    // --- Shift -----------------------------------------------------------------------------------

    /** Moves {@code tab} one place left ({@code -1}) or right ({@code +1}) among its pane's tabs, keeping it selected. */
    private static void shift(Tab tab, int by) {
        TabPane pane = tab.getTabPane();
        if (pane == null) {
            return;
        }
        int index = pane.getTabs().indexOf(tab);
        int target = index + by;
        if (index < 0 || target < 0 || target >= pane.getTabs().size()) {
            return;
        }
        NbfxTabPane.moveTab(pane, tab, target);
        pane.getSelectionModel().select(tab);
    }

    /** The screen position of {@code tab}'s header (its graphic), or {@code null} if it is not showing. */
    private static Point2D screenLocationOf(Tab tab) {
        Node graphic = tab.getGraphic();
        if (graphic == null || graphic.getScene() == null) {
            return null;
        }
        Bounds b = graphic.localToScreen(graphic.getBoundsInLocal());
        return b == null ? null : new Point2D(b.getMinX(), b.getMinY());
    }
}

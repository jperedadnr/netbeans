package com.gluonhq.netbeans.nbfx.ui.actions;

import com.gluonhq.netbeans.nbfx.ui.context.EditorContexts;
import com.gluonhq.netbeans.nbfx.ui.project.ProjectSwitcher;
import com.gluonhq.netbeans.nbfx.ui.session.AppState;
import com.gluonhq.netbeans.nbfx.ui.session.DocumentCloser;
import com.gluonhq.netbeans.nbfx.ui.shell.TabContextMenu;
import com.gluonhq.netbeans.nbfx.ui.shell.ToolBarContainer;
import com.gluonhq.netbeans.nbfx.menus.MenuRegistrations;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.actions.ActionLayerReader;
import com.gluonhq.netbeans.nbfx.api.actions.ActionRegistry;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.FxActionRef;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.EditorSettings;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.api.actions.RunnableCommand;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Builds the application menu bar and tool bars from the shared {@link ActionRegistry}, and
 * registers the launcher-owned project commands. All controls are derived from {@link Command}s
 * (text, accelerator, enabled state, action), so the UI stays free of per-control behavior and
 * enablement follows context automatically.
 * <p>
 * Menu items render an icon, text and accelerator; tool bar buttons render only the icon and
 * expose the text plus shortcut through a tooltip. Actions are grouped into a File tool bar
 * (New / Open Project, Save / Save All), a Clipboard tool bar (Cut / Copy / Paste) and an Edit
 * tool bar (Undo / Redo). Each tool bar carries a leading drag handle used to relocate it within
 * the {@link ToolBarContainer}.
 */
public final class ActionBars {

    private static final Logger LOG = Logger.getLogger(ActionBars.class.getName());

    private static final String DRAG_HANDLE_ICON = "ToolbarXP.png";

    private static final double TOOLBAR_ICON_SIZE = 24;
    private static final double MENU_ICON_SIZE = 16;
    private static final double DRAG_HANDLE_WIDTH = 12;
    private static final double DRAG_HANDLE_HEIGHT = 24;

    /** Maps a command id to its icon resource (relative to this class' package). */
    private static final Map<String, String> ICONS = new HashMap<>();
    static {
        ICONS.put(ActionIds.NEW_PROJECT, "newProject24.png");
        ICONS.put(ActionIds.OPEN_PROJECT, "openProject24.png");
        ICONS.put(ActionIds.SAVE, "save.png");
        ICONS.put(ActionIds.SAVE_ALL, "saveAll24.png");
        ICONS.put(ActionIds.CUT, "cut.png");
        ICONS.put(ActionIds.COPY, "copy.png");
        ICONS.put(ActionIds.PASTE, "paste.png");
        ICONS.put(ActionIds.FIND, "find.png");
        ICONS.put(ActionIds.UNDO, "undo24.png");
        ICONS.put(ActionIds.REDO, "redo24.png");
        ICONS.put(ActionIds.SELECT_PROJECTS, "projectTab.png");
        ICONS.put(ActionIds.SELECT_FILES, "filesTab.png");
        ICONS.put(ActionIds.BUILD, "build.png");
        ICONS.put(ActionIds.CLEAN_BUILD, "cleanBuild.png");
        ICONS.put(ActionIds.CLEAN, "clean24.gif");
        ICONS.put(ActionIds.TEST, "test.png");
        ICONS.put(ActionIds.RUN, "run.png");
        ICONS.put(ActionIds.DEBUG_FILE, "debug.png");
    }

    private final ActionRegistry registry;
    private final EditorSettings editorSettings;
    private final Map<String, Image> imageCache = new HashMap<>();

    // File/editor dispatch (set via configureFileDispatch before the main bars are built).
    private ObservableValue<Boolean> treeFocused;

    /** The main window's tool bars, captured in {@link #createToolBars} so the View menu can toggle them. */
    private ToolBarContainer toolBarContainer;

    /**
     * Collects the window-scoped commands created while a single window's bars are being built.
     * FX-thread confined; only non-null for the duration of {@link #createDetachedBars}.
     */
    private List<Command> scopedSink;

    // Recent-projects wiring (set via configureRecentProjects before createMenuBar).
    private Supplier<List<AppState.RecentProject>> recentProjectsSupplier;
    private Consumer<File> openProjectHandler;
    private Runnable clearRecentHandler;
    private BiFunction<File, String, Node> projectIconResolver;
    private Menu recentMenu;

    // Project switching (set via configureProjectSwitching before createMenuBar).
    private ProjectRegistry projects;
    private ProjectSwitcher projectSwitcher;
    private Function<OpenProject, Node> openProjectIconResolver;

    public ActionBars() {
        this.registry = Lookup.getDefault().lookup(ActionRegistry.class);
        this.editorSettings = Lookup.getDefault().lookup(EditorSettings.class);
    }

    /**
     * Configures the "Open Recent Project" submenu: a supplier of recent projects, a handler to open a chosen project
     * directory, an action to clear the list, and a resolver that builds each project's icon from its stored icon name.
     */
    public void configureRecentProjects(Supplier<List<AppState.RecentProject>> recentProjects, Consumer<File> openProject,
            Runnable clearRecent, BiFunction<File, String, Node> projectIcon) {
        this.recentProjectsSupplier = recentProjects;
        this.openProjectHandler = openProject;
        this.clearRecentHandler = clearRecent;
        this.projectIconResolver = projectIcon;
    }

    /**
     * Configures focus-based file/editor dispatch for the main window's Cut/Copy/Paste/Undo/Redo:
     * when {@code treeFocused} is true those actions target their file counterparts (the
     * {@code file.*} commands from the registry), otherwise the editor. Must be called before
     * {@link #createMenuBar(ObservableValue)} / {@link #createToolBars(ObservableValue)}.
     */
    public void configureFileDispatch(ObservableValue<Boolean> treeFocused) {
        this.treeFocused = treeFocused;
    }

    /**
     * Registers the launcher-owned project commands (Open / Close Project) into the registry.
     * New Project comes from the wizard module. Must be called before
     * {@link #createMenuBar(ObservableValue)} / {@link #createToolBars(ObservableValue)} so their
     * controls can be built.
     */
    public void registerProjectCommands(Runnable openProject,
            Runnable closeProject, Runnable closeAllProjects, ObservableValue<Boolean> closeDisabled) {
        if (registry == null) {
            LOG.warning("No ActionRegistry found; project actions will not be available");
            return;
        }
        registry.register(new RunnableCommand(ActionIds.OPEN_PROJECT, message("CTL_OpenProjectCommand"),
                new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                openProject));
        registry.register(new RunnableCommand(ActionIds.CLOSE_PROJECT, message("CTL_CloseProjectCommand"),
                null, closeProject, closeDisabled));
        registry.register(new RunnableCommand(ActionIds.CLOSE_ALL_PROJECTS, message("CTL_CloseAllProjectsCommand"),
                null, closeAllProjects, closeDisabled));
    }

    /** The launcher-owned window actions and their enablement, passed to {@link #registerWindowCommands}. */
    public record WindowActions(
            Runnable selectProjects, Runnable selectFiles, Runnable selectEditor, Runnable resetWindows,
            Runnable closeDocument, ObservableValue<Boolean> closeDocumentDisabled,
            Runnable closeAllDocuments, ObservableValue<Boolean> closeAllDisabled,
            Runnable closeOtherDocuments, ObservableValue<Boolean> closeOtherDisabled) {
    }

    /**
     * Registers the launcher-owned Window-menu commands (Projects / Files / Editor selection, Reset
     * Windows, and the Close Document/All/Other actions) into the registry. Must be called before
     * {@link #createMenuBar(ObservableValue)} so the Window menu can be built.
     */
    public void registerWindowCommands(WindowActions actions) {
        if (registry == null) {
            LOG.warning("No ActionRegistry found; window actions will not be available");
            return;
        }
        registry.register(new RunnableCommand(ActionIds.SELECT_PROJECTS, message("CTL_SelectProjectsCommand"),
                new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN), actions.selectProjects()));
        registry.register(new RunnableCommand(ActionIds.SELECT_FILES, message("CTL_SelectFilesCommand"),
                new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN), actions.selectFiles()));
        registry.register(new RunnableCommand(ActionIds.SELECT_EDITOR, message("CTL_SelectEditorCommand"),
                new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN), actions.selectEditor()));
        registry.register(new RunnableCommand(ActionIds.RESET_WINDOWS, message("CTL_ResetWindowsCommand"),
                null, actions.resetWindows()));
        registry.register(new RunnableCommand(ActionIds.CLOSE_DOCUMENT, message("CTL_CloseDocumentCommand"),
                new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
                actions.closeDocument(), actions.closeDocumentDisabled()));
        registry.register(new RunnableCommand(ActionIds.CLOSE_ALL_DOCUMENTS, message("CTL_CloseAllDocumentsCommand"),
                new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                actions.closeAllDocuments(), actions.closeAllDisabled()));
        registry.register(new RunnableCommand(ActionIds.CLOSE_OTHER_DOCUMENTS, message("CTL_CloseOtherDocumentsCommand"),
                null, actions.closeOtherDocuments(), actions.closeOtherDisabled()));
    }

    /**
     * Registers the launcher-owned entry of the editor's context menu: Select in Projects, which
     * reveals the file of the globally active editor (the right-clicked one, as opening the menu
     * focuses it) in the Projects view. The menu entry itself is declared in the layer by
     * {@link MenuRegistrations}.
     */
    public void registerEditorContextCommands(Consumer<FileObject> selectInProjects) {
        if (registry == null) {
            LOG.warning("No ActionRegistry found; editor context actions will not be available");
            return;
        }
        ObservableValue<EditorDocument> active = EditorContexts.activeDocument();
        ObservableValue<Boolean> disabled = active == null
                ? new SimpleBooleanProperty(true)
                : Bindings.createBooleanBinding(() -> active.getValue() == null, active);
        registry.register(new RunnableCommand(ActionIds.SELECT_IN_PROJECTS, message("CTL_SelectInProjectsCommand"),
                null, () -> {
                    EditorDocument document = active == null ? null : active.getValue();
                    if (document != null) {
                        selectInProjects.accept(document.getFileObject());
                    }
                }, disabled));
    }

    /**
     * Configures project switching: registers the Next/Previous Project commands and gives the
     * Window menu what it needs to list the open projects - the registry to observe, the
     * {@code switcher} that performs the switch, and a resolver for each project's icon. Must be
     * called before {@link #createMenuBar(ObservableValue)}.
     */
    public void configureProjectSwitching(ProjectRegistry registry, ProjectSwitcher switcher,
            Function<OpenProject, Node> projectIcon) {
        this.projects = registry;
        this.projectSwitcher = switcher;
        this.openProjectIconResolver = projectIcon;
        if (this.registry == null || switcher == null) {
            return;
        }
        this.registry.register(new RunnableCommand(ActionIds.NEXT_PROJECT, message("CTL_NextProjectCommand"),
                new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
                switcher::next, switcher.disabled()));
        this.registry.register(new RunnableCommand(ActionIds.PREVIOUS_PROJECT, message("CTL_PreviousProjectCommand"),
                new KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
                switcher::previous, switcher.disabled()));
    }

    /**
     * Builds the menu bar + tool bars for a detached editor window, with its active-document actions scoped
     * to {@code scope}, that window's own selected editor.
     * <p>
     * The window-scoped commands created here observe documents that outlive the window, so they are
     * collected into {@code scopedCommands}; the caller must {@link Command#dispose() dispose} them when
     * the window closes.
     */
    public VBox createDetachedBars(ObservableValue<EditorDocument> scope, List<Command> scopedCommands) {
        List<Command> previous = scopedSink;
        scopedSink = scopedCommands;
        try {
            return new VBox(createDetachedMenuBar(scope), createDetachedToolBars(scope));
        } finally {
            scopedSink = previous;
        }
    }

    /**
     * Builds the main menu bar, scoping the active-document actions to {@code mainScope}, so they act on the main
     * window regardless of which window has focus. Save All and project actions stay global.
     */
    public MenuBar createMenuBar(ObservableValue<EditorDocument> mainScope) {
        List<Menu> menus = new ArrayList<>();
        // The menus are declared in the layer (NbFx/Menus), so the launcher does not know any of them:
        // each module contributes its own menu, in position order.
        for (MenuSpec spec : menuSpecs()) {
            Menu menu = createRegisteredMenu(spec, mainScope);
            if (menu != null && !menu.getItems().isEmpty()) {
                menus.add(menu);
            }
        }
        MenuBar menuBar = new MenuBar(menus.toArray(Menu[]::new));
        // TODO: Fix https://bugs.openjdk.org/browse/JDK-8388508
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    /**
     * Builds the menu declared by {@code spec}: the shell's dynamic menus (File with the Recent
     * Projects submenu, Edit with the file-vs-editor dispatch, View with its toggles, Window with the
     * open projects and the configure submenu) have their own builders; every other menu is built
     * from its layer references.
     */
    private Menu createRegisteredMenu(MenuSpec spec, ObservableValue<EditorDocument> mainScope) {
        Menu menu = switch (spec.id()) {
            case "File" -> createFileMenu(mainScope);
            case "Edit" -> createEditMenu(mainScope, treeFocused);
            case "View" -> createViewMenu();
            case "Window" -> createWindowMenu(mainScope);
            default -> createRefMenu(spec, mainScope);
        };
        if (menu != null && !spec.title().isEmpty()) {
            menu.setText(spec.title());
        }
        return menu;
    }

    /** Builds a menu from the layer references in {@code spec}, or {@code null} when it has none. */
    private Menu createRefMenu(MenuSpec spec, ObservableValue<EditorDocument> mainScope) {
        if (spec.refs().isEmpty()) {
            return null;
        }
        Menu menu = new Menu(spec.title().isEmpty() ? spec.id() : spec.title());
        for (FxActionRef ref : spec.refs()) {
            MenuItem item = createOptionalMenuItem(ref.actionId(), mainScope, null);
            if (item == null) {
                continue;
            }
            addSeparatorIfNeeded(menu, ref);
            menu.getItems().add(item);
        }
        return menu.getItems().isEmpty() ? null : menu;
    }

    /** The menus declared under {@code NbFx/Menus}, ordered by their {@code position}. */
    private static List<MenuSpec> menuSpecs() {
        FileObject root = FileUtil.getConfigFile("NbFx/Menus");
        if (root == null) {
            return List.of();
        }
        List<MenuSpec> specs = new ArrayList<>();
        for (FileObject folder : root.getChildren()) {
            if (!folder.isFolder()) {
                continue;
            }
            int position = folder.getAttribute("position") instanceof Integer p ? p : Integer.MAX_VALUE;
            String displayName = folder.getAttribute("displayName") instanceof String s ? s : "";
            specs.add(new MenuSpec(folder.getName(), menuTitle(folder.getName(), displayName), position,
                    ActionLayerReader.read(folder)));
        }
        specs.sort(Comparator.comparingInt(MenuSpec::position).thenComparing(MenuSpec::id));
        return specs;
    }

    private static String menuTitle(String id, String displayName) {
        if (!displayName.isEmpty()) {
            return displayName;
        }
        try {
            return message("Menu." + id.toLowerCase());
        } catch (RuntimeException ex) {
            return id;
        }
    }

    /** A menu declared in the layer: its id, title, order and references. */
    private record MenuSpec(String id, String title, int position, List<FxActionRef> refs) {
    }

    /**
     * Builds the File menu from the {@code NbFx/Menus/File} layer entries, expanding the Recent
     * Projects placeholder into the dynamically built submenu; falls back to the hard-coded entries
     * while the layer registration is unavailable.
     */
    private Menu createFileMenu(ObservableValue<EditorDocument> mainScope) {
        Menu fileMenu = new Menu(message("Menu.file"));
        recentMenu = new Menu(message("Menu.openRecent"));
        refreshRecentProjects();
        List<FxActionRef> refs = ActionLayerReader.read("NbFx/Menus/File");
        if (!refs.isEmpty()) {
            for (FxActionRef ref : refs) {
                if (MenuRegistrations.OPEN_RECENT.equals(ref.actionId())) {
                    fileMenu.getItems().add(recentMenu);
                    continue;
                }
                MenuItem item = createOptionalMenuItem(ref.actionId(), mainScope, treeFocused);
                if (item == null) {
                    continue;
                }
                if (ref.separatorBefore() && !fileMenu.getItems().isEmpty()
                        && !(fileMenu.getItems().get(fileMenu.getItems().size() - 1) instanceof SeparatorMenuItem)) {
                    fileMenu.getItems().add(new SeparatorMenuItem());
                }
                fileMenu.getItems().add(item);
            }
            return fileMenu;
        }
        addMenuItems(fileMenu,
                createMenuItem(ActionIds.NEW_PROJECT, mainScope, treeFocused),
                createMenuItem(ActionIds.OPEN_PROJECT, mainScope, treeFocused),
                recentMenu,
                createMenuItem(ActionIds.CLOSE_PROJECT, mainScope, treeFocused),
                createMenuItem(ActionIds.CLOSE_ALL_PROJECTS, mainScope, treeFocused),
                new SeparatorMenuItem(),
                createMenuItem(ActionIds.SAVE, mainScope, treeFocused),
                createMenuItem(ActionIds.SAVE_ALL, mainScope, treeFocused));
        return fileMenu;
    }

    /**
     * Builds the View menu: a Toolbars submenu with a check item per main-window tool bar (to hide
     * or show it) plus a Reset Toolbars action, and Show Line Numbers / Show Breadcrumbs toggles
     * bound to the shared {@link EditorSettings}. The Toolbars submenu is only present once
     * {@link #createToolBars} has created the main tool bars.
     */
    private Menu createViewMenu() {
        Menu viewMenu = new Menu(message("Menu.view"));
        if (toolBarContainer != null) {
            Menu toolbarsMenu = new Menu(message("Menu.view.toolbars"));
            for (ToolBar bar : toolBarContainer.getToolBars()) {
                CheckMenuItem item = new CheckMenuItem(toolBarName(bar.getId()));
                item.selectedProperty().bindBidirectional(bar.visibleProperty());
                toolbarsMenu.getItems().add(item);
            }
            toolbarsMenu.getItems().add(new SeparatorMenuItem());
            MenuItem reset = new MenuItem(message("Menu.view.resetToolbars"));
            reset.setOnAction(e -> toolBarContainer.resetArrangement());
            toolbarsMenu.getItems().add(reset);
            viewMenu.getItems().add(toolbarsMenu);
        }
        if (editorSettings != null) {
            CheckMenuItem lineNumbers = new CheckMenuItem(message("Menu.view.showLineNumbers"));
            lineNumbers.selectedProperty().bindBidirectional(editorSettings.showLineNumbers());
            viewMenu.getItems().add(lineNumbers);
            CheckMenuItem breadcrumbs = new CheckMenuItem(message("Menu.view.showBreadcrumbs"));
            breadcrumbs.selectedProperty().bindBidirectional(editorSettings.showBreadcrumbs());
            viewMenu.getItems().add(breadcrumbs);
        }
        return viewMenu;
    }

    /** Human-readable name for a tool bar id, from the resource bundle (falls back to the id). */
    private String toolBarName(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        try {
            return message("Toolbar.name." + id);
        } catch (RuntimeException ex) {
            return id;
        }
    }

    /**
     * Builds the Window menu: Projects / Files / Editor (and Usages / Search Results, when a module
     * contributes the command) selection, the Configure Window submenu (the
     * layout actions of the tab context menus, applied to the focused tab), Reset Windows, the
     * Close Document / Close All / Close Other actions, all routed through the shared registry, and
     * finally the project section - Next / Previous Project plus one entry per open project.
     */
    private Menu createWindowMenu(ObservableValue<EditorDocument> mainScope) {
        Menu windowMenu = new Menu(message("Menu.window"));
        List<FxActionRef> refs = ActionLayerReader.read("NbFx/Menus/Window");
        if (!refs.isEmpty()) {
            for (FxActionRef ref : refs) {
                if (MenuRegistrations.CONFIGURE_WINDOW.equals(ref.actionId())) {
                    addSeparatorIfNeeded(windowMenu, ref);
                    windowMenu.getItems().add(TabContextMenu.createConfigureWindowMenu());
                    continue;
                }
                MenuItem item = createOptionalMenuItem(ref.actionId(), mainScope, null);
                if (item == null) {
                    continue;
                }
                addSeparatorIfNeeded(windowMenu, ref);
                windowMenu.getItems().add(item);
            }
        } else {
            addMenuItems(windowMenu,
                    createMenuItem(ActionIds.SELECT_PROJECTS, mainScope, null),
                    createMenuItem(ActionIds.SELECT_FILES, mainScope, null),
                    createMenuItem(ActionIds.SELECT_EDITOR, mainScope, null),
                    createOptionalMenuItem(ActionIds.SELECT_USAGES, mainScope),
                    createOptionalMenuItem(ActionIds.SELECT_SEARCH_RESULTS, mainScope),
                    new SeparatorMenuItem(),
                    TabContextMenu.createConfigureWindowMenu(),
                    createMenuItem(ActionIds.RESET_WINDOWS, mainScope, null),
                    new SeparatorMenuItem(),
                    createMenuItem(ActionIds.CLOSE_DOCUMENT, mainScope, null),
                    createMenuItem(ActionIds.CLOSE_ALL_DOCUMENTS, mainScope, null),
                    createMenuItem(ActionIds.CLOSE_OTHER_DOCUMENTS, mainScope, null));
            if (projectSwitcher != null) {
                addMenuItems(windowMenu,
                        new SeparatorMenuItem(),
                        createMenuItem(ActionIds.NEXT_PROJECT, mainScope, null),
                        createMenuItem(ActionIds.PREVIOUS_PROJECT, mainScope, null));
            }
        }
        if (projectSwitcher != null) {
            installOpenProjects(windowMenu);
        }
        return windowMenu;
    }

    /** Adds a separator before the next item when the reference asks for one and it is not already there. */
    private static void addSeparatorIfNeeded(Menu menu, FxActionRef ref) {
        ObservableList<MenuItem> items = menu.getItems();
        if (ref.separatorBefore() && !items.isEmpty()
                && !(items.get(items.size() - 1) instanceof SeparatorMenuItem)) {
            items.add(new SeparatorMenuItem());
        }
    }

    /**
     * Appends the open projects to the Window menu, the way a window manager lists its windows: one
     * checked entry per project, in the order they were opened, marking the selected one. The
     * entries are rebuilt whenever a project is opened or closed, and re-marked when the selection
     * changes - which happens on its own as the user moves between the navigator and the editor
     * tabs, so the menu always names the project the project-scoped actions apply to.
     */
    private void installOpenProjects(Menu windowMenu) {
        int fixedItems = windowMenu.getItems().size();
        Runnable rebuild = () -> {
            windowMenu.getItems().remove(fixedItems, windowMenu.getItems().size());
            List<OpenProject> open = List.copyOf(projects.getOpenProjects());
            if (open.isEmpty()) {
                return;
            }
            // A group per rebuild: the discarded items must not stay referenced by a lasting one.
            ToggleGroup group = new ToggleGroup();
            windowMenu.getItems().add(new SeparatorMenuItem());
            for (OpenProject project : open) {
                windowMenu.getItems().add(createProjectItem(project, group));
            }
        };
        rebuild.run();
        projects.getOpenProjects().addListener((ListChangeListener<OpenProject>) change -> rebuild.run());
        projects.selectedProjectProperty().subscribe(selected -> markSelectedProject(windowMenu, fixedItems, selected));
    }

    /** A Window-menu entry for {@code project}: its name, icon, and a check mark while it is the selected one. */
    private RadioMenuItem createProjectItem(OpenProject project, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(project.getDisplayName());
        // Project names are user data: an underscore in one must not become a mnemonic.
        item.setMnemonicParsing(false);
        item.setToggleGroup(group);
        item.setUserData(project);
        item.setSelected(project.equals(projects.getSelected()));
        if (openProjectIconResolver != null) {
            Node icon = openProjectIconResolver.apply(project);
            if (icon != null) {
                item.setGraphic(icon);
            }
        }
        item.setOnAction(e -> projectSwitcher.switchTo(project));
        return item;
    }

    /** Moves the check mark to {@code selected} among the project entries after {@code fixedItems}. */
    private static void markSelectedProject(Menu windowMenu, int fixedItems, OpenProject selected) {
        List<MenuItem> items = windowMenu.getItems();
        for (int i = fixedItems; i < items.size(); i++) {
            if (items.get(i) instanceof RadioMenuItem item) {
                item.setSelected(item.getUserData() != null && item.getUserData().equals(selected));
            }
        }
    }

    /**
     * Builds the Edit menu, in NetBeans' order: Undo / Redo, Cut / Copy / Paste, Find Selection /
     * Find Next / Find Previous, Find..., Replace..., Find in Projects... and - when a module
     * registers the command - Find Usages. The find items are optional too: they come with the
     * editor actions and search modules.
     */
    private Menu createEditMenu(ObservableValue<EditorDocument> scope, ObservableValue<Boolean> preferFile) {
        Menu editMenu = new Menu(message("Menu.edit"));
        List<FxActionRef> refs = ActionLayerReader.read("NbFx/Menus/Edit");
        if (!refs.isEmpty()) {
            for (FxActionRef ref : refs) {
                MenuItem item = createOptionalMenuItem(ref.actionId(), scope, preferFile);
                if (item == null) {
                    continue;
                }
                if (ref.separatorBefore() && !editMenu.getItems().isEmpty()
                        && !(editMenu.getItems().get(editMenu.getItems().size() - 1) instanceof SeparatorMenuItem)) {
                    editMenu.getItems().add(new SeparatorMenuItem());
                }
                editMenu.getItems().add(item);
            }
            return editMenu;
        }
        // Fallback while the layer registration is unavailable.
        addMenuItems(editMenu,
                createMenuItem(ActionIds.UNDO, scope, preferFile),
                createMenuItem(ActionIds.REDO, scope, preferFile),
                new SeparatorMenuItem(),
                createMenuItem(ActionIds.CUT, scope, preferFile),
                createMenuItem(ActionIds.COPY, scope, preferFile),
                createMenuItem(ActionIds.PASTE, scope, preferFile));
        addMenuGroup(editMenu,
                createOptionalMenuItem(ActionIds.FIND_SELECTION, scope),
                createOptionalMenuItem(ActionIds.FIND_NEXT, scope),
                createOptionalMenuItem(ActionIds.FIND_PREVIOUS, scope));
        addMenuGroup(editMenu,
                // Shortcut+F: the Find bar in the editor, Find in Projects on the selection in the tree
                createOptionalMenuItem(ActionIds.FIND, scope, preferFile),
                createOptionalMenuItem(ActionIds.REPLACE, scope),
                createOptionalMenuItem(ActionIds.FIND_IN_PROJECTS, scope),
                createOptionalMenuItem(ActionIds.REPLACE_IN_PROJECTS, scope));
        addMenuGroup(editMenu,
                createOptionalMenuItem(ActionIds.FIND_USAGES, scope));
        return editMenu;
    }

    /** Appends {@code items} after a separator, skipping the {@code null}s; nothing when all are {@code null}. */
    private static void addMenuGroup(Menu menu, MenuItem... items) {
        if (Arrays.stream(items).allMatch(Objects::isNull)) {
            return;
        }
        menu.getItems().add(new SeparatorMenuItem());
        addMenuItems(menu, items);
    }

    /**
     * A menu item for a command another module may or may not contribute (e.g. Find Usages):
     * {@code null}, without a warning, when no command is registered under {@code commandId}.
     */
    private MenuItem createOptionalMenuItem(String commandId, ObservableValue<EditorDocument> scope) {
        return createOptionalMenuItem(commandId, scope, null);
    }

    /** As {@link #createOptionalMenuItem(String, ObservableValue)}, dispatching to the file counterpart while the tree is focused. */
    private MenuItem createOptionalMenuItem(String commandId, ObservableValue<EditorDocument> scope,
            ObservableValue<Boolean> preferFile) {
        if (registry == null || registry.find(commandId).isEmpty()) {
            return null;
        }
        return createMenuItem(commandId, scope, preferFile);
    }

    /**
     * Builds a menu bar for a detached editor window, with its active-document actions scoped to
     * {@code scope}, that window's selected editor. Contains a File menu and the same Edit and View menus
     * as the main window, so editor and save actions act on the detached window while it is focused.
     */
    private MenuBar createDetachedMenuBar(ObservableValue<EditorDocument> scope) {
        Menu fileMenu = new Menu(message("Menu.file"));
        addMenuItems(fileMenu,
                createMenuItem(ActionIds.SAVE, scope, null),
                createMenuItem(ActionIds.SAVE_ALL, scope, null));

        Menu helpMenu = new Menu(message("Menu.help"));
        MenuBar menuBar = new MenuBar(fileMenu, createEditMenu(scope, null),
                createViewMenu(), createDetachedWindowMenu(scope), helpMenu);
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    /**
     * Builds a reduced Window menu for a detached editor window: Configure Window, Close Document /
     * Close All / Close Other Documents, scoped to that window's selected editor ({@code scope}), plus Next / Previous
     * Project. Navigator/layout items (Projects / Files / Editor / Reset Windows) are omitted, as
     * they only apply to the main window, and so is the list of open projects: the selection is
     * shown - and switched to - in the main window's navigator.
     */
    private Menu createDetachedWindowMenu(ObservableValue<EditorDocument> scope) {
        Menu windowMenu = new Menu(message("Menu.window"));
        ObservableList<EditorDocument> documents = openDocuments();

        MenuItem close = new MenuItem(message("CTL_CloseDocumentCommand"));
        close.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
        close.setOnAction(e -> DocumentCloser.closeDocument(scope == null ? null : scope.getValue()));
        if (scope != null) {
            close.disableProperty().bind(Bindings.createBooleanBinding(() -> scope.getValue() == null, scope));
        }

        MenuItem closeAll = new MenuItem(message("CTL_CloseAllDocumentsCommand"));
        closeAll.setAccelerator(new KeyCodeCombination(KeyCode.W,
                KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        closeAll.setOnAction(e -> DocumentCloser.closeAllDocuments());
        closeAll.disableProperty().bind(Bindings.isEmpty(documents));

        MenuItem closeOther = new MenuItem(message("CTL_CloseOtherDocumentsCommand"));
        closeOther.setOnAction(e -> DocumentCloser.closeOtherDocuments(scope == null ? null : scope.getValue()));
        closeOther.disableProperty().bind(Bindings.size(documents).lessThan(2));

        addMenuItems(windowMenu, TabContextMenu.createConfigureWindowMenu(), new SeparatorMenuItem(),
                close, closeAll, closeOther);
        if (projectSwitcher != null) {
            addMenuItems(windowMenu,
                    new SeparatorMenuItem(),
                    createMenuItem(ActionIds.NEXT_PROJECT, scope, null),
                    createMenuItem(ActionIds.PREVIOUS_PROJECT, scope, null));
        }
        return windowMenu;
    }

    /** The shared, observable list of currently open documents (empty if no editor context is present). */
    private static ObservableList<EditorDocument> openDocuments() {
        return EditorContexts.openDocuments();
    }

    /**
     * Rebuilds the "Open Recent Project" submenu from the current recent list. Each entry opens its
     * project; a trailing action clears the list. When empty, a single disabled placeholder is shown.
     */
    public void refreshRecentProjects() {
        if (recentMenu == null) {
            return;
        }
        recentMenu.getItems().clear();
        List<AppState.RecentProject> recent = recentProjectsSupplier != null
                ? recentProjectsSupplier.get() : List.of();
        if (recent.isEmpty()) {
            MenuItem empty = new MenuItem(message("Menu.openRecent.empty"));
            empty.setDisable(true);
            recentMenu.getItems().add(empty);
            return;
        }
        for (AppState.RecentProject project : recent) {
            File dir = new File(project.path());
            MenuItem item = new MenuItem(dir.getName());
            item.setMnemonicParsing(false);
            if (projectIconResolver != null && project.iconName() != null) {
                Node icon = projectIconResolver.apply(dir, project.iconName());
                if (icon != null) {
                    item.setGraphic(icon);
                }
            }
            if (openProjectHandler != null) {
                item.setOnAction(e -> openProjectHandler.accept(dir));
            }
            recentMenu.getItems().add(item);
        }
        recentMenu.getItems().add(new SeparatorMenuItem());
        MenuItem clear = new MenuItem(message("Menu.openRecent.clear"));
        if (clearRecentHandler != null) {
            clear.setOnAction(e -> clearRecentHandler.run());
        }
        recentMenu.getItems().add(clear);
    }

    /**
     * Builds the three main tool bars (File, Clipboard, Edit), scoping the active-document actions
     * to {@code mainScope}, and hosts them in a {@link ToolBarContainer} that packs them to the left and top,
     * wraps them onto further rows when needed, and lets the user reorder them by dragging their leading drag handle.
     *
     * @return the tool bar container for the top area
     */
    public ToolBarContainer createToolBars(ObservableValue<EditorDocument> mainScope) {
        List<ToolBar> bars = new ArrayList<>();
        // The tool bars are declared in the layer (NbFx/Toolbars), so the launcher does not know any
        // of them: each module contributes its own bar, in position order.
        for (ToolBarSpec spec : toolbarSpecs()) {
            bars.add(createToolBar(spec.id(), mainScope, treeFocused, spec.commandIds()));
        }
        if (bars.isEmpty()) {
            // Fallback while no layer registration is available.
            bars.add(createToolBar("file", mainScope, treeFocused,
                    List.of(ActionIds.NEW_PROJECT, ActionIds.OPEN_PROJECT, ActionIds.SAVE, ActionIds.SAVE_ALL)));
            bars.add(createToolBar("clipboard", mainScope, treeFocused,
                    List.of(ActionIds.CUT, ActionIds.COPY, ActionIds.PASTE, ActionIds.FIND)));
            bars.add(createToolBar("edit", mainScope, treeFocused,
                    List.of(ActionIds.UNDO, ActionIds.REDO)));
        }
        toolBarContainer = new ToolBarContainer(bars.toArray(ToolBar[]::new));
        return toolBarContainer;
    }

    /** The tool bars declared under {@code NbFx/Toolbars}, ordered by their {@code position}. */
    private static List<ToolBarSpec> toolbarSpecs() {
        FileObject root = FileUtil.getConfigFile("NbFx/Toolbars");
        if (root == null) {
            return List.of();
        }
        List<ToolBarSpec> specs = new ArrayList<>();
        for (FileObject folder : root.getChildren()) {
            if (!folder.isFolder()) {
                continue;
            }
            List<String> ids = ActionLayerReader.read(folder).stream().map(FxActionRef::actionId).toList();
            if (ids.isEmpty()) {
                continue;
            }
            int position = folder.getAttribute("position") instanceof Integer p ? p : Integer.MAX_VALUE;
            specs.add(new ToolBarSpec(folder.getName(), position, ids));
        }
        specs.sort(Comparator.comparingInt(ToolBarSpec::position).thenComparing(ToolBarSpec::id));
        return specs;
    }

    /** A tool bar declared in the layer: its id, its order and the command ids of its buttons. */
    private record ToolBarSpec(String id, int position, List<String> commandIds) {
    }

    private ToolBar createToolBar(String id, ObservableValue<EditorDocument> scope,
            ObservableValue<Boolean> preferFile, List<String> commandIds) {
        ToolBar toolBar = new ToolBar();
        toolBar.setId(id);
        toolBar.getItems().add(createDragHandle());
        addButtons(toolBar, scope, preferFile, commandIds);
        return toolBar;
    }

    /**
     * Builds the tool bars for a detached editor window, scoping the active-document actions to
     * {@code scope}, the window's selected editor. Contains a File bar with just the save actions,
     * plus Clipboard and Edit bars. Unlike the main tool bars these carry no drag handle and are not relocatable.
     */
    private Region createDetachedToolBars(ObservableValue<EditorDocument> scope) {
        ToolBar fileBar = new ToolBar(new Separator());
        addButtons(fileBar, scope, null, ActionIds.SAVE, ActionIds.SAVE_ALL);
        mirrorMainVisibility(fileBar, "file");
        ToolBar clipboardBar = new ToolBar(new Separator());
        addButtons(clipboardBar, scope, null, ActionIds.CUT, ActionIds.COPY, ActionIds.PASTE, ActionIds.FIND);
        mirrorMainVisibility(clipboardBar, "clipboard");
        ToolBar editBar = new ToolBar(new Separator());
        addButtons(editBar, scope, null, ActionIds.UNDO, ActionIds.REDO);
        mirrorMainVisibility(editBar, "edit");
        return new HBox(fileBar, clipboardBar, editBar);
    }

    /**
     * Makes a detached window's tool bar follow the visibility of the matching main tool bar, so
     * hiding/showing a tool bar from the View menu (or Reset Toolbars) applies to every window. The
     * main tool bar's {@code visibleProperty} is the single source of truth; the bind uses a weak
     * listener internally, so it is released when the detached window is closed.
     */
    private void mirrorMainVisibility(ToolBar detachedBar, String id) {
        ToolBar mainBar = mainToolBar(id);
        if (mainBar != null) {
            detachedBar.visibleProperty().bind(mainBar.visibleProperty());
            detachedBar.managedProperty().bind(detachedBar.visibleProperty());
        }
    }

    /** Returns the main-window tool bar with the given id, or {@code null} if the tool bars aren't built yet. */
    private ToolBar mainToolBar(String id) {
        if (toolBarContainer == null) {
            return null;
        }
        for (ToolBar bar : toolBarContainer.getToolBars()) {
            if (id.equals(bar.getId())) {
                return bar;
            }
        }
        return null;
    }

    private void addButtons(ToolBar toolBar, ObservableValue<EditorDocument> scope,
            ObservableValue<Boolean> preferFile, List<String> commandIds) {
        for (String commandId : commandIds) {
            Button button = createButton(commandId, scope, preferFile);
            if (button != null) {
                toolBar.getItems().add(button);
            }
        }
    }

    private void addButtons(ToolBar toolBar, ObservableValue<EditorDocument> scope,
            ObservableValue<Boolean> preferFile, String... commandIds) {
        addButtons(toolBar, scope, preferFile, List.of(commandIds));
    }

    private Button createDragHandle() {
        Button handle = new Button();
        ImageView graphic = toolbarIcon(DRAG_HANDLE_ICON, DRAG_HANDLE_WIDTH, DRAG_HANDLE_HEIGHT);
        if (graphic != null) {
            handle.setGraphic(graphic);
        }
        handle.getStyleClass().add("toolbar-drag-handle");
        handle.setFocusTraversable(false);
        handle.setTooltip(new Tooltip(message("Toolbar.dragHandle.tooltip")));
        return handle;
    }

    private static void addMenuItems(Menu menu, MenuItem... items) {
        for (MenuItem item : items) {
            if (item != null) {
                menu.getItems().add(item);
            }
        }
    }

    private MenuItem createMenuItem(String commandId, ObservableValue<EditorDocument> scope,
            ObservableValue<Boolean> preferFile) {
        Command command = resolve(commandId, scope, preferFile);
        if (command == null) {
            return null;
        }
        ImageView graphic = menuIcon(ICONS.get(commandId));
        MenuItem item = new MenuItem(command.getText(), graphic);
        item.disableProperty().bind(command.disabledProperty());
        item.setOnAction(e -> command.run());
        KeyCombination accelerator = command.getAccelerator();
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        return item;
    }

    private Button createButton(String commandId, ObservableValue<EditorDocument> scope,
            ObservableValue<Boolean> preferFile) {
        Command command = resolve(commandId, scope, preferFile);
        if (command == null) {
            return null;
        }
        Button button = new Button();
        button.getStyleClass().add("toolbar-button");
        // Action buttons must not take keyboard focus: otherwise clicking one steals focus from the
        // editor/tree, and if the action then disables the button (e.g. Redo emptying the redo stack)
        // focus escapes to another node (the tree), flipping the focus-based Cut/Copy/Paste/Undo/Redo
        // dispatch to the wrong target.
        button.setFocusTraversable(false);
        ImageView graphic = toolbarIcon(ICONS.get(commandId), TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE);
        if (graphic != null) {
            button.setGraphic(graphic);
        } else {
            button.setText(command.getText());
        }
        button.disableProperty().bind(command.disabledProperty());
        button.setOnAction(e -> command.run());
        KeyCombination accelerator = command.getAccelerator();
        String tooltip = accelerator == null
                ? command.getText()
                : command.getText() + " (" + accelerator.getDisplayText() + ")";
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    /** Builds a tool bar graphic by fit-scaling the full-resolution source in an {@link ImageView} */
    private ImageView toolbarIcon(String resourceName, double fitWidth, double fitHeight) {
        Image image = fullImage(resourceName);
        if (image == null) {
            return null;
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(fitWidth);
        view.setFitHeight(fitHeight);
        view.setPreserveRatio(true);
        return view;
    }

    /** Builds a menu graphic from the dedicated 16&times;16 icon variant (named {@code <base>-16.png}). */
    private ImageView menuIcon(String resourceName) {
        if (resourceName == null) {
            return null;
        }
        String menuResource = resourceName.replaceFirst("\\.png$", "-16.png");
        return toolbarIcon(menuResource, MENU_ICON_SIZE, MENU_ICON_SIZE);
    }

    /**
     * Loads (and caches) the full-resolution source image for the given resource name, falling back
     * to the 16&times;16 variant for icons that only ship in that size (menu-only commands).
     */
    private Image fullImage(String resourceName) {
        if (resourceName == null) {
            return null;
        }
        return imageCache.computeIfAbsent(resourceName, name -> {
            var url = ActionBars.class.getResource(name);
            if (url == null) {
                String smallResource = name.replaceFirst("(?<!-16)\\.png$", "-16.png");
                url = ActionBars.class.getResource(smallResource);
            }
            if (url == null) {
                LOG.warning("Icon resource not found: " + name);
                return null;
            }
            return new Image(url.toExternalForm());
        });
    }

    /**
     * Resolves the command for {@code commandId}: a window-scoped variant bound to {@code scope} for active-document
     * actions, or the shared global command for global actions or if no scope is given. When {@code preferFile} is
     * given and the command has a file counterpart (Cut/Copy/Paste/Undo/Redo, and Find when the Find in Projects
     * module registers {@code file.find}), returns a {@link DispatchingCommand} that targets files while the tree is
     * focused and the editor otherwise.
     */
    private Command resolve(String commandId, ObservableValue<EditorDocument> scope,
            ObservableValue<Boolean> preferFile) {
        if (registry == null) {
            return null;
        }
        Command editor = registry.createScoped(commandId, scope)
                .map(this::trackScoped)
                .or(() -> registry.find(commandId))
                .orElse(null);
        if (preferFile != null) {
            Command file = fileCommandFor(commandId);
            if (file != null && editor != null) {
                return new DispatchingCommand(editor, file, preferFile);
            }
        }
        if (editor == null) {
            LOG.warning("No command registered for id: " + commandId);
        }
        return editor;
    }

    /** Records a freshly created window-scoped command so the owning window can dispose it on close. */
    private Command trackScoped(Command command) {
        if (scopedSink != null) {
            scopedSink.add(command);
        }
        return command;
    }

    /** Returns the registered file-scoped counterpart of {@code commandId}, or {@code null} if none applies. */
    private Command fileCommandFor(String commandId) {
        String fileId = switch (commandId) {
            case ActionIds.CUT -> ActionIds.FILE_CUT;
            case ActionIds.COPY -> ActionIds.FILE_COPY;
            case ActionIds.PASTE -> ActionIds.FILE_PASTE;
            case ActionIds.UNDO -> ActionIds.FILE_UNDO;
            case ActionIds.REDO -> ActionIds.FILE_REDO;
            case ActionIds.FIND -> ActionIds.FILE_FIND;
            default -> null;
        };
        return fileId == null ? null : registry.find(fileId).orElse(null);
    }

    private static String message(String key) {
        return NbBundle.getMessage(ActionBars.class, key);
    }
}

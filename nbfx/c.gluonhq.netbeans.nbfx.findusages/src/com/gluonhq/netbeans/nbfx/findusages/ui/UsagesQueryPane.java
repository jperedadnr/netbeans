package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.editor.EditorPreview;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import com.gluonhq.netbeans.nbfx.findusages.model.UsagesModel;
import com.gluonhq.netbeans.nbfx.findusages.query.QueryOptions;
import com.gluonhq.netbeans.nbfx.findusages.query.UsagesQuery;
import com.gluonhq.netbeans.nbfx.findusages.query.UsagesQuery.State;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsageTreeBuilder.Flavour;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsagesSettings.ViewState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The content of one "Usages of X" tab: two vertical tool bars (actions, filters), the results
 * tree (headed by its "Usages of X [N occurrences]" row) and, when toggled on, a read-only preview
 * of the selected usage, mirroring NetBeans' Find Usages window. Flavour, filters and the preview
 * toggle persist through {@link UsagesSettings}.
 */
final class UsagesQueryPane extends HBox {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final UsagesQuery query;
    private final UsagesSettings settings;
    private final UsageFilters filters = new UsageFilters();
    private final ObjectProperty<Flavour> flavour = new SimpleObjectProperty<>(Flavour.LOGICAL);
    private final BooleanProperty previewVisible = new SimpleBooleanProperty(false);
    private final ReadOnlyObjectWrapper<ViewState> viewState = new ReadOnlyObjectWrapper<>();
    private final TreeView<UsageNode> tree = new TreeView<>();
    private final TreeItem<UsageNode> root = new TreeItem<>();
    private final TreeItem<UsageNode> header = new TreeItem<>();
    private final Label status = new Label();
    private final ToggleButton expandToggle = toggle("collapseTree", "TIP_CollapseAll");
    private MenuButton optionsButton;
    private final SplitPane split = new SplitPane();
    private final EditorPreview preview = new EditorPreview(message("LBL_PreviewPlaceholder"));
    private final List<ButtonBase> filterButtons = new ArrayList<>();
    private boolean rebuildPending;

    UsagesQueryPane(UsagesQuery query) {
        this(query, new UsagesSettings(), null);
    }

    /**
     * @param initial how this tab looked when it was saved, or {@code null} to start as the last
     *        tab the user adjusted ({@code settings}' flavour, filters and preview)
     */
    UsagesQueryPane(UsagesQuery query, UsagesSettings settings, ViewState initial) {
        this.query = query;
        this.settings = settings;
        if (initial != null) {
            initial.applyTo(filters);
            flavour.set(initial.flavour());
            previewVisible.set(initial.preview());
        } else {
            settings.load(filters);
            flavour.set(settings.getFlavour());
            previewVisible.set(settings.isPreviewVisible());
        }
        viewState.set(ViewState.of(flavour.get(), previewVisible.get(), filters));
        getStyleClass().add("usages-query");
        setFillHeight(true);

        // The "Usages of X [N occurrences]" row is a leaf at the projects' level, as in NetBeans:
        // no disclosure arrow, nothing to collapse.
        root.setExpanded(true);
        tree.setRoot(root);
        tree.setShowRoot(false);
        tree.setMinHeight(0);
        tree.setCellFactory(view -> new UsageTreeCell());
        tree.getStyleClass().add("usages-tree");
        tree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                openSelected();
            }
        });
        tree.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelected();
                e.consume();
            }
        });
        tree.setContextMenu(createContextMenu());

        status.getStyleClass().add("usages-status");
        status.setMaxWidth(Double.MAX_VALUE);
        status.visibleProperty().bind(status.textProperty().isNotEmpty());
        status.managedProperty().bind(status.visibleProperty());

        VBox results = new VBox(tree, status);
        results.setMinHeight(0);
        VBox.setVgrow(tree, Priority.ALWAYS);

        split.setOrientation(Orientation.HORIZONTAL);
        split.getItems().add(results);
        previewVisible.subscribe(visible -> {
            settings.setPreviewVisible(visible);
            updateViewState();
            if (visible && !split.getItems().contains(preview)) {
                split.getItems().add(preview);
                split.setDividerPositions(0.5);
                showInPreview(selectedUsage());
            } else if (!visible) {
                split.getItems().remove(preview);
                preview.clear();
            }
        });
        tree.getSelectionModel().selectedItemProperty().subscribe(item -> {
            if (previewVisible.get()) {
                showInPreview(selectedUsage());
            }
        });
        // The preview document is created lazily once the pane is on screen (it needs the editor
        // infrastructure) and released with the pane, so a closed query keeps no buffer alive.
        sceneProperty().subscribe(scene -> {
            if (scene != null && previewVisible.get()) {
                preview.restore();
            } else if (scene == null) {
                preview.clear();
            }
        });
        split.setMinHeight(0);
        HBox.setHgrow(split, Priority.ALWAYS);
        setMinHeight(0);

        getChildren().add(createActionsBar());
        if (query.getTarget().isFilterable()) {
            getChildren().add(createFiltersBar());
        }
        getChildren().add(split);

        query.getUsages().addListener((ListChangeListener<Usage>) change -> {
            scheduleRebuild();
            updateFilterAvailability();
        });
        query.stateProperty().subscribe(state -> updateRootText());
        query.messageProperty().subscribe(message -> updateStatus());
        filters.addListener(obs -> {
            scheduleRebuild();
            updateViewState();
        });
        settings.watch(filters);
        flavour.subscribe(f -> {
            settings.setFlavour(f);
            updateViewState();
            rebuild();
        });
        updateFilterAvailability();
        rebuild();
    }

    UsagesQuery getQuery() {
        return query;
    }

    /** How this tab looks now - flavour, preview, disabled filters - for the session to save. */
    ReadOnlyObjectProperty<ViewState> viewStateProperty() {
        return viewState.getReadOnlyProperty();
    }

    private void updateViewState() {
        viewState.set(ViewState.of(flavour.get(), previewVisible.get(), filters));
    }

    /** The name shown in this pane's tab title. */
    String getTargetName() {
        return query.getTarget().getSimpleName();
    }

    void requestTreeFocus() {
        tree.requestFocus();
    }

    // -- tool bars -------------------------------------------------------------------------------

    private ToolBar createActionsBar() {
        Button refresh = button("refresh", "TIP_Refresh", query::refresh);
        refresh.disableProperty().bind(query.stateProperty().isEqualTo(State.RUNNING));
        Button stop = button("stop", "TIP_Stop", query::cancel);
        stop.disableProperty().bind(query.stateProperty().isNotEqualTo(State.RUNNING));
        Button prev = button("prevmatch", "TIP_PreviousOccurrence", () -> step(-1));
        Button next = button("nextmatch", "TIP_NextOccurrence", () -> step(1));

        // One toggle for expand / collapse all: selected while the tree is expanded.
        expandToggle.setSelected(true);
        expandToggle.selectedProperty().subscribe(expanded -> {
            ((ImageView) expandToggle.getGraphic()).setImage(UsagesIcons.image(expanded ? "collapseTree" : "expandTree"));
            expandToggle.getTooltip().setText(message(expanded ? "TIP_CollapseAll" : "TIP_ExpandAll"));
        });
        expandToggle.selectedProperty().subscribe(expanded -> {
            if (expanded) {
                setExpanded(root, true);
            } else {
                collapseAll();
            }
        });

        ToggleGroup flavours = new ToggleGroup();
        ToggleButton logical = toggle("logical_view", "TIP_LogicalView");
        ToggleButton physical = toggle("file_view", "TIP_PhysicalView");
        logical.setToggleGroup(flavours);
        physical.setToggleGroup(flavours);
        (flavour.get() == Flavour.PHYSICAL ? physical : logical).setSelected(true);
        flavours.selectedToggleProperty().subscribe((was, now) -> {
            if (now == null) {
                // Keep one flavour selected: re-select the one that was just unselected.
                flavours.selectToggle(was);
            } else {
                flavour.set(now == logical ? Flavour.LOGICAL : Flavour.PHYSICAL);
            }
        });

        ToggleButton previewToggle = toggle("preview", "TIP_Preview");
        previewToggle.selectedProperty().bindBidirectional(previewVisible);

        ToolBar bar = toolBar(refresh, stop, new Separator(Orientation.HORIZONTAL), prev, next,
                new Separator(Orientation.HORIZONTAL), expandToggle,
                new Separator(Orientation.HORIZONTAL), logical, physical,
                new Separator(Orientation.HORIZONTAL), previewToggle, createOptionsButton());
        bar.getStyleClass().add("usages-actions");
        return bar;
    }

    /**
     * The query's options - search in comments, scope - in a small menu, in place of NetBeans'
     * Find Usages dialog. A change runs the query again and becomes the default for the next.
     */
    private MenuButton createOptionsButton() {
        MenuButton button = new MenuButton();
        decorate(button, "edit_parameters", "TIP_Options");
        button.getStyleClass().add("usages-options-button");
        optionsButton = button;
        updateOptionsIndicator();

        CheckMenuItem comments = new CheckMenuItem(message("OPT_SearchInComments"));
        comments.setSelected(query.getOptions().searchComments());
        comments.setDisable(!query.getTarget().isFilterable());
        comments.selectedProperty().subscribe(on -> applyOptions(query.getOptions().withSearchComments(on)));

        ToggleGroup scopes = new ToggleGroup();
        List<MenuItem> items = new ArrayList<>(List.of(comments, new SeparatorMenuItem()));
        for (QueryOptions.Scope scope : QueryOptions.Scope.values()) {
            RadioMenuItem item = new RadioMenuItem(message("SCOPE_" + scope.name(), scopeArgument(scope)));
            item.setToggleGroup(scopes);
            item.setSelected(query.getOptions().scope() == scope);
            item.setUserData(scope);
            items.add(item);
        }
        scopes.selectedToggleProperty().subscribe(toggle -> {
            if (toggle != null && toggle.getUserData() != query.getOptions().scope()) {
                applyOptions(query.getOptions().withScope((QueryOptions.Scope) toggle.getUserData()));
            }
        });
        button.getItems().setAll(items);
        return button;
    }

    /** The scope searched, for the header: the number of projects, the project's name, or the number of open files. */
    private String scopeLabel() {
        return switch (query.getOptions().scope()) {
            case ALL_PROJECTS -> {
                ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
                int count = registry == null ? 0 : registry.getOpenProjects().size();
                yield count == 1 ? message("LBL_ScopeOneProject") : message("LBL_ScopeProjects", count);
            }
            case CURRENT_PROJECT, CURRENT_PACKAGE, CURRENT_FILE -> message("LBL_ScopeNamed", scopeArgument(query.getOptions().scope()));
            case OPEN_FILES -> {
                Set<FileObject> files = query.getSources().files();
                int count = files == null ? 0 : files.size();
                yield count == 1 ? message("LBL_ScopeOneFile") : message("LBL_ScopeFiles", count);
            }
        };
    }

    /** What a "current ..." scope item and header name: the project, the package or the file. */
    private String scopeArgument(QueryOptions.Scope scope) {
        FileObject file = query.getTarget().getFile();
        return switch (scope) {
            case CURRENT_PROJECT -> currentProjectName();
            case CURRENT_PACKAGE -> {
                String name = query.getSources().packageNameOf(file);
                yield name.isEmpty() ? "<default package>" : name;
            }
            case CURRENT_FILE -> file.getNameExt();
            default -> "";
        };
    }

    /** The name of the project owning the target's file - the "current project" of this query - or the file's folder. */
    private String currentProjectName() {
        FileObject file = query.getTarget().getFile();
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject project = registry == null ? null : registry.ownerOf(file);
        return project != null ? project.getDisplayName() : file.getParent().getNameExt();
    }

    private void applyOptions(QueryOptions options) {
        if (options.equals(query.getOptions())) {
            return;
        }
        settings.setOptions(options);
        UsagesModel model = Lookup.getDefault().lookup(UsagesModel.class);
        if (model != null) {
            model.setDefaultOptions(options);
        }
        query.rerun(options);
        updateOptionsIndicator();
    }

    /** The options button looks pressed, like an enabled filter, while the query searches less than every project. */
    private void updateOptionsIndicator() {
        optionsButton.pseudoClassStateChanged(SELECTED, query.getOptions().scope() != QueryOptions.DEFAULT.scope());
    }

    private ToolBar createFiltersBar() {
        List<Node> items = new ArrayList<>();
        for (UsageFilters.Kind kind : UsageFilters.Kind.values()) {
            ToggleButton button = toggle(kind.icon, kind.tooltipKey);
            button.selectedProperty().bindBidirectional(filters.enabled(kind));
            button.setUserData(kind);
            filterButtons.add(button);
            items.add(button);
        }
        items.add(new Separator(Orientation.HORIZONTAL));
        for (UsageFilters.Root rootKind : UsageFilters.Root.values()) {
            ToggleButton button = toggle(rootKind.icon, rootKind.tooltipKey);
            button.selectedProperty().bindBidirectional(filters.enabled(rootKind));
            button.setUserData(rootKind);
            filterButtons.add(button);
            items.add(button);
        }
        ToolBar bar = toolBar(items.toArray(Node[]::new));
        bar.getStyleClass().add("usages-filters");
        return bar;
    }

    /** A filter is offered only when the results hold usages of its category. */
    private void updateFilterAvailability() {
        List<Usage> usages = query.getUsages();
        for (ButtonBase button : filterButtons) {
            Object category = button.getUserData();
            boolean present = category instanceof UsageFilters.Kind kind
                    ? usages.stream().anyMatch(u -> UsageFilters.Kind.of(u) == kind)
                    : usages.stream().anyMatch(u -> UsageFilters.Root.of(u) == category);
            button.setDisable(!present);
        }
    }

    /** A vertical tool bar; items that do not fit go to its overflow menu. */
    private static ToolBar toolBar(Node... items) {
        ToolBar bar = new ToolBar(items);
        bar.setOrientation(Orientation.VERTICAL);
        bar.getStyleClass().add("usages-toolbar");
        bar.setMinHeight(0);
        return bar;
    }

    private static Button button(String icon, String tooltipKey, Runnable action) {
        Button button = new Button();
        decorate(button, icon, tooltipKey);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static ToggleButton toggle(String icon, String tooltipKey) {
        ToggleButton button = new ToggleButton();
        decorate(button, icon, tooltipKey);
        return button;
    }

    private static void decorate(ButtonBase button, String icon, String tooltipKey) {
        button.setGraphic(UsagesIcons.view(icon));
        button.setTooltip(new Tooltip(message(tooltipKey)));
        button.getStyleClass().add("usages-tool-button");
        button.setFocusTraversable(false);
    }

    private ContextMenu createContextMenu() {
        MenuItem open = new MenuItem(message("CTX_GoToSource"));
        open.setOnAction(e -> openSelected());
        open.disableProperty().bind(Bindings.createBooleanBinding(() -> selectedUsage() == null,
                tree.getSelectionModel().selectedItemProperty()));
        MenuItem expandAll = new MenuItem(message("TIP_ExpandAll"));
        expandAll.setOnAction(e -> expandToggle.setSelected(true));
        MenuItem collapseAll = new MenuItem(message("TIP_CollapseAll"));
        collapseAll.setOnAction(e -> expandToggle.setSelected(false));
        return new ContextMenu(open, new SeparatorMenuItem(), expandAll, collapseAll);
    }

    // -- tree ------------------------------------------------------------------------------------

    /** Coalesces the per-file result batches into one rebuild per pulse. */
    private void scheduleRebuild() {
        if (rebuildPending) {
            return;
        }
        rebuildPending = true;
        Platform.runLater(() -> {
            rebuildPending = false;
            rebuild();
        });
    }

    private void rebuild() {
        Usage selected = selectedUsage();
        List<TreeItem<UsageNode>> items = new ArrayList<>();
        items.add(header);
        items.addAll(UsageTreeBuilder.build(List.copyOf(query.getUsages()), flavour.get(), filters));
        root.getChildren().setAll(items);
        if (!expandToggle.isSelected()) {
            collapseAll();
        }
        updateRootText();
        if (selected != null) {
            TreeItem<UsageNode> item = itemOf(selected);
            if (item != null) {
                tree.getSelectionModel().select(item);
            }
        }
    }

    private void updateRootText() {
        String name = query.getTarget().getDisplayName();
        int shown = countUsages(root);
        int filtered = query.getUsages().size() - shown;
        String text = filtered > 0 ? message("LBL_RootFiltered", name, shown, filtered)
                : shown == 1 ? message("LBL_RootOne", name) : message("LBL_Root", name, shown);
        text += " " + scopeLabel();
        if (query.getState() == State.RUNNING) {
            text += " " + message("LBL_Searching");
        }
        header.setValue(UsageNode.group(UsageNode.Kind.HEADER, text, () -> UsagesIcons.view("findusages")));
        updateStatus();
    }

    private void updateStatus() {
        State state = query.getState();
        String message = query.messageProperty().get();
        status.setText(state == State.FAILED || state == State.CANCELLED ? (message == null ? "" : message) : "");
        status.pseudoClassStateChanged(ERROR, state == State.FAILED);
    }

    private static int countUsages(TreeItem<UsageNode> item) {
        if (item.getValue() != null && item.getValue().usage() != null) {
            return 1;
        }
        int count = 0;
        for (TreeItem<UsageNode> child : item.getChildren()) {
            count += countUsages(child);
        }
        return count;
    }

    private void collapseAll() {
        for (TreeItem<UsageNode> child : root.getChildren()) {
            setExpanded(child, false);
        }
        root.setExpanded(true);
    }

    private static void setExpanded(TreeItem<UsageNode> item, boolean expanded) {
        if (item.getChildren().isEmpty()) {
            return;
        }
        item.setExpanded(expanded);
        for (TreeItem<UsageNode> child : item.getChildren()) {
            setExpanded(child, expanded);
        }
    }

    /** Reveals {@code usage} in the preview; {@code null} (a group row, a rebuild) leaves the last one on. */
    private void showInPreview(Usage usage) {
        if (usage != null) {
            preview.show(usage.file(), usage.start(), usage.end());
        }
    }

    private Usage selectedUsage() {
        TreeItem<UsageNode> item = tree.getSelectionModel().getSelectedItem();
        return item == null || item.getValue() == null ? null : item.getValue().usage();
    }

    private void openSelected() {
        Usage usage = selectedUsage();
        if (usage != null) {
            open(usage);
        }
    }

    private static void open(Usage usage) {
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (contentManager != null) {
            contentManager.openFile(usage.file(), usage.start(), usage.end());
        }
    }

    /** Selects and opens the usage {@code delta} positions away from the selected one, wrapping around. */
    private void step(int delta) {
        List<TreeItem<UsageNode>> leaves = new ArrayList<>();
        collectUsages(root, leaves);
        if (leaves.isEmpty()) {
            return;
        }
        int index = leaves.indexOf(tree.getSelectionModel().getSelectedItem());
        int target = index < 0
                ? (delta > 0 ? 0 : leaves.size() - 1)
                : Math.floorMod(index + delta, leaves.size());
        TreeItem<UsageNode> item = leaves.get(target);
        expandPath(item);
        tree.getSelectionModel().select(item);
        tree.scrollTo(tree.getRow(item));
        open(item.getValue().usage());
    }

    private static void collectUsages(TreeItem<UsageNode> item, List<TreeItem<UsageNode>> into) {
        if (item.getValue() != null && item.getValue().usage() != null) {
            into.add(item);
            return;
        }
        for (TreeItem<UsageNode> child : item.getChildren()) {
            collectUsages(child, into);
        }
    }

    private static void expandPath(TreeItem<UsageNode> item) {
        for (TreeItem<UsageNode> parent = item.getParent(); parent != null; parent = parent.getParent()) {
            parent.setExpanded(true);
        }
    }

    private TreeItem<UsageNode> itemOf(Usage usage) {
        List<TreeItem<UsageNode>> leaves = new ArrayList<>();
        collectUsages(root, leaves);
        for (TreeItem<UsageNode> leaf : leaves) {
            if (usage.equals(leaf.getValue().usage())) {
                return leaf;
            }
        }
        return null;
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(UsagesQueryPane.class, key, args);
    }
}

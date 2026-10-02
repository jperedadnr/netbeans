package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.NavigatorProvider;
import com.gluonhq.netbeans.nbfx.api.editor.EditorPreview;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FileResult;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FindModel;
import com.gluonhq.netbeans.nbfx.findinprojects.model.Issue;
import com.gluonhq.netbeans.nbfx.findinprojects.model.TextMatch;
import com.gluonhq.netbeans.nbfx.findinprojects.query.FindQuery;
import com.gluonhq.netbeans.nbfx.findinprojects.query.FindQuery.State;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchScope;
import com.gluonhq.netbeans.nbfx.findinprojects.replace.ReplaceTask;
import com.gluonhq.netbeans.nbfx.findinprojects.replace.ReplaceTask.Outcome;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultCells.CheckState;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultCells.Checks;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.FindSettings.ColumnState;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.Detail;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.File;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.Folder;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.Summary;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultTreeBuilder.Flavour;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.SavedSearch.ViewState;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultTreeBuilder.Item;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Subscription;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The content of one results tab - NetBeans' {@code BasicSearchResultsPanel}: a vertical tool bar
 * (Rescan, Stop, Modify Criteria, previous / next match, expand / collapse, tree or list, Show
 * Preview), the results as a {@link TreeTableView} headed by the summary row, with a status line
 * under it, and - when toggled on - a read-only preview of the selected occurrence on the right.
 * The view flavour, the column layout, the preview toggle and its divider persist through
 * {@link FindSettings}.
 */
final class SearchResultsPane extends HBox {

    static final KeyCombination PREVIOUS = new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN);
    static final KeyCombination NEXT = new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.SHORTCUT_DOWN);

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    /** Rows, in pixels: the icon plus the cell padding. */
    private static final double ROW_HEIGHT = 22;

    private final FindQuery query;
    private final FindModel model;
    private final FindSettings settings;
    private final ObjectProperty<Flavour> flavour = new SimpleObjectProperty<>(Flavour.TREE);
    private final TreeTableView<ResultRow> table = new TreeTableView<>();
    private final TreeItem<ResultRow> root = new TreeItem<>(new Summary("", null, "", 0));
    private final Label status = new Label();
    private final ToggleButton expandToggle = toggle("collapseTree", "TIP_CollapseAll");
    private final SplitPane split = new SplitPane();
    private final BooleanProperty previewVisible = new SimpleBooleanProperty(false);
    private final EditorPreview preview = new EditorPreview(message("LBL_PreviewPlaceholder"));
    private Subscription dividerSubscription = Subscription.EMPTY;
    private final Set<FileResult> hiddenFiles = new HashSet<>();
    private final Set<Detail> hiddenDetails = new HashSet<>();
    /** Replace search: the occurrences (by row key) the user unchecked, the button, and what a replace did. */
    private final boolean replaceMode;
    private final Set<String> unchecked = new HashSet<>();
    private final IntegerProperty checkedCount = new SimpleIntegerProperty();
    private final BooleanProperty replacing = new SimpleBooleanProperty(false);
    private final Button replaceButton = new Button();
    private final List<Issue> replaceIssues = new ArrayList<>();
    private String replaceStatus;
    private final ResultTreeBuilder builder = new ResultTreeBuilder(this::rootOf, this::rootNameOf);
    private boolean rebuildPending;
    private boolean columnSavePending;
    private boolean restoringColumns;
    private long startedAt = System.nanoTime();
    private int shownFiles;
    private int shownMatches;

    SearchResultsPane(FindQuery query, FindModel model) {
        this(query, model, FindSettings.getDefault());
    }

    SearchResultsPane(FindQuery query, FindModel model, FindSettings settings) {
        this(query, model, settings, null);
    }

    /**
     * @param view how the tab looked when it was saved, or {@code null} for the defaults: the
     *             flavour and preview toggle as last changed in any tab
     */
    SearchResultsPane(FindQuery query, FindModel model, FindSettings settings, ViewState view) {
        this.query = Objects.requireNonNull(query);
        this.model = Objects.requireNonNull(model);
        this.settings = Objects.requireNonNull(settings);
        this.replaceMode = query.getCriteria().isReplace();
        flavour.set(view == null ? settings.getViewMode() : view.flavour());
        getStyleClass().add("search-results");
        setFillHeight(true);
        setMinHeight(0);

        buildTable();

        status.getStyleClass().add("search-results-status");
        status.setMaxWidth(Double.MAX_VALUE);
        status.visibleProperty().bind(status.textProperty().isNotEmpty());
        status.managedProperty().bind(status.visibleProperty());

        VBox results = new VBox(table, replaceMode ? createReplaceFooter() : status);
        results.setMinHeight(0);
        VBox.setVgrow(table, Priority.ALWAYS);

        split.setOrientation(Orientation.HORIZONTAL);
        split.getItems().add(results);
        split.setMinHeight(0);
        HBox.setHgrow(split, Priority.ALWAYS);
        // Show Preview: the selected occurrence in a read-only editor on the right, the divider persisted.
        previewVisible.set(view == null ? settings.isPreviewVisible() : view.preview());
        previewVisible.subscribe(visible -> {
            settings.setPreviewVisible(visible);
            if (visible && !split.getItems().contains(preview)) {
                split.getItems().add(preview);
                split.setDividerPositions(settings.getResultsDivider());
                dividerSubscription = split.getDividers().get(0).positionProperty()
                        .subscribe(position -> settings.setResultsDivider(position.doubleValue()));
                showInPreview(selectedRow());
            } else if (!visible) {
                dividerSubscription.unsubscribe();
                dividerSubscription = Subscription.EMPTY;
                split.getItems().remove(preview);
                preview.clear();
            }
        });
        table.getSelectionModel().selectedItemProperty().subscribe(item -> {
            if (previewVisible.get()) {
                showInPreview(item == null ? null : item.getValue());
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

        getChildren().addAll(createToolBar(), split);

        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (PREVIOUS.match(e)) {
                step(-1);
                e.consume();
            } else if (NEXT.match(e)) {
                step(1);
                e.consume();
            }
        });

        query.getResults().addListener((ListChangeListener<FileResult>) change -> scheduleRebuild());
        query.getIssues().addListener((ListChangeListener<Issue>) change -> updateSummary());
        query.stateProperty().subscribe(state -> {
            if (state == State.RUNNING) {
                startedAt = System.nanoTime();
                hiddenFiles.clear();
                hiddenDetails.clear();
                unchecked.clear();
                replaceIssues.clear();
                replaceStatus = null;
                scheduleRebuild();
            }
            updateSummary();
            updateStatus();
        });
        query.messageProperty().subscribe(message -> updateStatus());
        query.progressProperty().subscribe(progress -> updateStatus());
        flavour.subscribe(f -> {
            settings.setViewMode(f);
            rebuild();
        });
        rebuild();
    }

    FindQuery getQuery() {
        return query;
    }

    void requestTableFocus() {
        table.requestFocus();
    }

    /** The results table, for tests. */
    TreeTableView<ResultRow> table() {
        return table;
    }

    /** How the results are arranged. */
    ObjectProperty<Flavour> flavourProperty() {
        return flavour;
    }

    // -- table ---------------------------------------------------------------------------------

    private void buildTable() {
        root.setExpanded(true);
        table.setRoot(root);
        table.setShowRoot(true);
        table.setMinHeight(0);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setTableMenuButtonVisible(true);
        table.setPlaceholder(new Label(""));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getStyleClass().add("search-results-table");
        table.setSortPolicy(view -> {
            sortChildren(view.getRoot(), view.getComparator());
            return true;
        });

        TreeTableColumn<ResultRow, ResultRow> name = ResultCells.nameColumn(this::showIssues, replaceMode ? new RowChecks() : null);
        table.getColumns().setAll(List.of(name, ResultCells.matchesColumn(), ResultCells.pathColumn(),
                ResultCells.sizeColumn(), ResultCells.modifiedColumn()));
        table.setTreeColumn(name);
        // The columns always fill the table, as NetBeans' outline in its AUTO_RESIZE_SUBSEQUENT_COLUMNS
        // mode: a resize is shared in proportion to the preferred widths (Name's is the largest),
        // a dragged edge is absorbed by the columns after it, and there is no horizontal scroll bar.
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        restoreColumns();
        for (TreeTableColumn<ResultRow, ?> column : table.getColumns()) {
            column.widthProperty().subscribe(w -> scheduleColumnSave());
            column.visibleProperty().subscribe(v -> scheduleColumnSave());
        }
        table.getColumns().addListener((ListChangeListener<TreeTableColumn<ResultRow, ?>>) _ -> scheduleColumnSave());

        table.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                openSelected();
            }
        });
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                removeSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.SPACE && replaceMode) {
                toggleSelectedChecks();
                e.consume();
            }
        });
        table.setContextMenu(createContextMenu());
    }

    /**
     * Orders the children of every item: folders before files before occurrences, then by the
     * sorted column - occurrences always by their position -, or by the walk order when no column
     * is sorted.
     */
    private static void sortChildren(TreeItem<ResultRow> item, Comparator<TreeItem<ResultRow>> byColumn) {
        if (item == null || item.getChildren().isEmpty()) {
            return;
        }
        boolean details = item.getChildren().getFirst().getValue() instanceof Detail;
        Comparator<TreeItem<ResultRow>> order = Comparator.comparingInt(i -> i.getValue().rank());
        order = order.thenComparing(byColumn == null || details ? BY_SEQUENCE : byColumn);
        FXCollections.sort(item.getChildren(), order);
        for (TreeItem<ResultRow> child : item.getChildren()) {
            sortChildren(child, byColumn);
        }
    }

    private static final Comparator<TreeItem<ResultRow>> BY_SEQUENCE =
            Comparator.comparingInt(item -> item instanceof Item i ? i.sequence() : Integer.MAX_VALUE);

    private void restoreColumns() {
        List<ColumnState> saved = settings.getResultsColumns();
        if (saved.isEmpty()) {
            return;
        }
        restoringColumns = true;
        try {
            Map<String, TreeTableColumn<ResultRow, ?>> byId = new HashMap<>();
            for (TreeTableColumn<ResultRow, ?> column : table.getColumns()) {
                byId.put(column.getId(), column);
            }
            List<TreeTableColumn<ResultRow, ?>> ordered = new ArrayList<>();
            for (ColumnState state : saved) {
                TreeTableColumn<ResultRow, ?> column = byId.remove(state.id());
                if (column != null) {
                    column.setPrefWidth(state.width());
                    // the Name column's visibility is bound: it is always shown
                    if (!column.visibleProperty().isBound()) {
                        column.setVisible(state.visible());
                    }
                    ordered.add(column);
                }
            }
            ordered.addAll(byId.values());
            table.getColumns().setAll(ordered);
        } finally {
            restoringColumns = false;
        }
    }

    /** Persists the column order, widths and visibility once per pulse. */
    private void scheduleColumnSave() {
        if (restoringColumns || columnSavePending) {
            return;
        }
        columnSavePending = true;
        Platform.runLater(() -> {
            columnSavePending = false;
            List<ColumnState> states = new ArrayList<>();
            for (TreeTableColumn<ResultRow, ?> column : table.getColumns()) {
                // restored as the preferred widths the constrained policy shares the table from
                states.add(new ColumnState(column.getId(), column.getWidth(), column.isVisible()));
            }
            settings.setResultsColumns(states);
        });
    }

    // -- tool bar ------------------------------------------------------------------------------

    private ToolBar createToolBar() {
        Button rescan = button("refresh", "TIP_Rescan", query::rerun);
        rescan.disableProperty().bind(query.stateProperty().isEqualTo(State.RUNNING));
        Button stop = button("stop", "TIP_Stop", query::cancel);
        stop.disableProperty().bind(query.stateProperty().isNotEqualTo(State.RUNNING));
        Button modify = button("edit_parameters", "TIP_ModifyCriteria", this::modifyCriteria);
        Button prev = button("prevmatch", "TIP_PreviousMatch", () -> step(-1));
        Button next = button("nextmatch", "TIP_NextMatch", () -> step(1));

        // One toggle for expand / collapse all: selected while the tree is expanded.
        expandToggle.setSelected(true);
        expandToggle.selectedProperty().subscribe(expanded -> {
            ((ImageView) expandToggle.getGraphic()).setImage(SearchIcons.image(expanded ? "collapseTree" : "expandTree"));
            expandToggle.getTooltip().setText(message(expanded ? "TIP_CollapseAll" : "TIP_ExpandAll"));
            setExpanded(root.getChildren(), expanded);
        });

        ToggleGroup flavours = new ToggleGroup();
        ToggleButton flat = toggle("file_view", "TIP_FlatView");
        ToggleButton tree = toggle("logical_view", "TIP_TreeView");
        flat.setToggleGroup(flavours);
        tree.setToggleGroup(flavours);
        (flavour.get() == Flavour.FLAT ? flat : tree).setSelected(true);
        flavours.selectedToggleProperty().subscribe((was, now) -> {
            if (now == null) {
                // keep one flavour selected: re-select the one just unselected
                flavours.selectToggle(was);
            } else {
                flavour.set(now == flat ? Flavour.FLAT : Flavour.TREE);
            }
        });

        ToggleButton previewToggle = toggle("preview", "TIP_Preview");
        previewToggle.selectedProperty().bindBidirectional(previewVisible);

        ToolBar bar = new ToolBar(rescan, stop, modify, new Separator(Orientation.HORIZONTAL), prev, next,
                new Separator(Orientation.HORIZONTAL), expandToggle,
                new Separator(Orientation.HORIZONTAL), tree, flat,
                new Separator(Orientation.HORIZONTAL), previewToggle);
        bar.setOrientation(Orientation.VERTICAL);
        bar.getStyleClass().add("search-toolbar");
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
        button.setGraphic(SearchIcons.view(icon));
        button.setTooltip(new Tooltip(message(tooltipKey)));
        button.getStyleClass().add("search-tool-button");
        button.setFocusTraversable(false);
    }

    /**
     * Re-opens the dialog seeded with this search's criteria; Find searches again in this tab, or
     * in a new one when "Open in New Tab" is checked.
     */
    private void modifyCriteria() {
        SearchCriteria current = query.getCriteria();
        List<FileObject> folders = current.scope() instanceof SearchScope.Folders(List<FileObject> roots) ? roots : null;
        FindInProjectsDialog dialog = new FindInProjectsDialog(windowOf(this), settings, folders, current.isReplace());
        dialog.seed(current);
        dialog.showAndWait().ifPresent(criteria -> {
            model.setLastCriteria(criteria);
            if (dialog.isOpenInNewTab()) {
                model.find(criteria, true);
            } else {
                query.rerun(criteria);
            }
        });
    }

    private void showIssues() {
        List<Issue> issues = new ArrayList<>(query.getIssues());
        issues.addAll(replaceIssues);
        new IssuesDialog(windowOf(this), issues).show();
    }

    // -- replace -------------------------------------------------------------------------------

    /**
     * The check boxes of a replace search: an occurrence is checked unless the user unchecked it,
     * a file or folder shows the state of the occurrences under it, and checking either checks
     * them all.
     */
    private final class RowChecks implements Checks {

        @Override
        public CheckState stateOf(TreeItem<ResultRow> item) {
            List<TreeItem<ResultRow>> details = new ArrayList<>();
            collect(item, details, Detail.class);
            long checked = details.stream().filter(d -> !unchecked.contains(d.getValue().key())).count();
            return checked == 0 ? CheckState.UNCHECKED
                    : checked == details.size() ? CheckState.CHECKED : CheckState.INDETERMINATE;
        }

        @Override
        public void setChecked(TreeItem<ResultRow> item, boolean checked) {
            List<TreeItem<ResultRow>> details = new ArrayList<>();
            collect(item, details, Detail.class);
            for (TreeItem<ResultRow> detail : details) {
                if (checked) {
                    unchecked.remove(detail.getValue().key());
                } else {
                    unchecked.add(detail.getValue().key());
                }
            }
            table.refresh();
            updateCheckedCount();
        }
    }

    /** The status line with the Replace button at its right, as NetBeans' replace results panel. */
    private HBox createReplaceFooter() {
        replaceButton.getStyleClass().add("search-replace-button");
        replaceButton.setOnAction(e -> replaceChecked());
        replaceButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> checkedCount.get() == 0 || replacing.get() || query.getState() == State.RUNNING,
                checkedCount, replacing, query.stateProperty()));
        checkedCount.subscribe(count -> replaceButton.setText(switch (count.intValue()) {
            case 0 -> message("BTN_ReplaceNone");
            case 1 -> message("BTN_ReplaceOne");
            default -> message("BTN_ReplaceMany", count);
        }));
        HBox.setHgrow(status, Priority.ALWAYS);
        HBox footer = new HBox(8, status, replaceButton);
        footer.getStyleClass().add("search-replace-footer");
        footer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return footer;
    }

    /**
     * Space on the selected rows: unchecks them all when every occurrence under them is checked,
     * else checks them all - so a multiple selection is excluded or included at once.
     */
    private void toggleSelectedChecks() {
        List<TreeItem<ResultRow>> selected = table.getSelectionModel().getSelectedItems().stream()
                .filter(item -> item != null && !(item.getValue() instanceof Summary))
                .toList();
        if (selected.isEmpty()) {
            return;
        }
        RowChecks checks = new RowChecks();
        boolean allChecked = selected.stream().allMatch(item -> checks.stateOf(item) == CheckState.CHECKED);
        for (TreeItem<ResultRow> item : selected) {
            checks.setChecked(item, !allChecked);
        }
    }

    /** The checked occurrences by file, in tree order. */
    private Map<FileResult, List<TextMatch>> checkedMatches() {
        List<TreeItem<ResultRow>> details = new ArrayList<>();
        collect(root, details, Detail.class);
        Map<FileResult, List<TextMatch>> selection = new LinkedHashMap<>();
        for (TreeItem<ResultRow> item : details) {
            Detail detail = (Detail) item.getValue();
            if (!unchecked.contains(detail.key())) {
                selection.computeIfAbsent(detail.result(), r -> new ArrayList<>()).add(detail.match());
            }
        }
        return selection;
    }

    private void updateCheckedCount() {
        if (replaceMode) {
            checkedCount.set(checkedMatches().values().stream().mapToInt(List::size).sum());
        }
    }

    /** Replaces the checked occurrences in the background, then removes their rows and reports. */
    private void replaceChecked() {
        Map<FileResult, List<TextMatch>> selection = checkedMatches();
        if (selection.isEmpty() || replacing.get()) {
            return;
        }
        replacing.set(true);
        replaceStatus = message("STATUS_Replacing");
        updateStatus();
        ReplaceTask.run(query.getCriteria(), selection).whenComplete((outcome, failure) -> Platform.runLater(() -> {
            replacing.set(false);
            if (failure != null) {
                replaceIssues.add(new Issue(query.getCriteria().scope().label(), String.valueOf(failure.getMessage())));
                replaceStatus = message("STATUS_ReplaceFailed");
            } else {
                onReplaced(outcome);
            }
            rebuild();
            updateStatus();
        }));
    }

    private void onReplaced(Outcome outcome) {
        outcome.replaced().forEach((result, matches) -> {
            for (TextMatch match : matches) {
                hiddenDetails.add(new Detail(result, match));
            }
        });
        replaceIssues.addAll(outcome.issues());
        StringBuilder text = new StringBuilder(message("STATUS_Replaced", outcome.replacedCount()));
        if (outcome.outdated() > 0) {
            text.append(' ').append(message("STATUS_Outdated", outcome.outdated()));
        }
        if (!outcome.issues().isEmpty()) {
            text.append(' ').append(message("STATUS_ReplaceIssues", outcome.issues().size()));
        }
        replaceStatus = text.toString();
    }

    /** The Replace button, for tests. */
    Button replaceButton() {
        return replaceButton;
    }

    /** The check boxes' model, for tests; {@code null} in a plain search. */
    Checks checks() {
        return replaceMode ? new RowChecks() : null;
    }

    /** The status line's text, for tests. */
    String statusText() {
        return status.getText();
    }

    private static Window windowOf(Node node) {
        return node.getScene() == null ? null : node.getScene().getWindow();
    }

    // -- context menu --------------------------------------------------------------------------

    private ContextMenu createContextMenu() {
        MenuItem open = new MenuItem(message("CTX_GoToSource"));
        open.setOnAction(e -> openSelected());
        MenuItem copyPath = new MenuItem(message("CTX_CopyFilePath"));
        copyPath.setOnAction(e -> copyPath());
        MenuItem select = new MenuItem(message("CTX_SelectInProjects"));
        select.setOnAction(e -> selectInProjects());
        MenuItem remove = new MenuItem(message("CTX_RemoveFromSearch"));
        remove.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        remove.setOnAction(e -> removeSelected());
        MenuItem expandAll = new MenuItem(message("TIP_ExpandAll"));
        expandAll.setOnAction(e -> expandToggle.setSelected(true));
        MenuItem collapseAll = new MenuItem(message("TIP_CollapseAll"));
        collapseAll.setOnAction(e -> expandToggle.setSelected(false));
        ContextMenu menu = new ContextMenu(open, copyPath, select, new SeparatorMenuItem(), remove,
                new SeparatorMenuItem(), expandAll, collapseAll);
        menu.setOnShowing(e -> {
            ResultRow row = selectedRow();
            open.setText(message(row instanceof Detail ? "CTX_GoToDetail" : "CTX_GoToSource"));
            open.setDisable(!(row instanceof File || row instanceof Detail));
            copyPath.setDisable(row == null || row.file() == null);
            select.setDisable(row == null || row.file() == null || navigatorOf(row.file()) == null);
            remove.setDisable(row == null || row instanceof Summary);
        });
        return menu;
    }

    private void copyPath() {
        ResultRow row = selectedRow();
        if (row != null && row.file() != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(FileUtil.getFileDisplayName(row.file()));
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    /** Reveals the selected row's file in the Projects (or Files) view that shows its project. */
    private void selectInProjects() {
        ResultRow row = selectedRow();
        NavigatorProvider navigator = row == null ? null : navigatorOf(row.file());
        if (navigator == null) {
            return;
        }
        ViewManager views = Lookup.getDefault().lookup(ViewManager.class);
        if (views != null) {
            views.show(navigator);
        }
        navigator.revealFile(row.file());
    }

    /** The first navigator whose projects hold {@code file}, or {@code null}. */
    private static NavigatorProvider navigatorOf(FileObject file) {
        if (file == null) {
            return null;
        }
        for (NavigatorProvider navigator : Lookup.getDefault().lookupAll(NavigatorProvider.class)) {
            for (FileObject root : navigator.getProjectRoots()) {
                if (root.equals(file) || FileUtil.isParentOf(root, file)) {
                    return navigator;
                }
            }
        }
        return null;
    }

    // -- tree ----------------------------------------------------------------------------------

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

    /** Builds the tree again from the results, keeping every row's expansion and the selection. */
    private void rebuild() {
        Map<String, Boolean> expansion = new HashMap<>();
        collectExpansion(root.getChildren(), expansion);
        List<String> selected = table.getSelectionModel().getSelectedItems().stream()
                .filter(item -> item != null && item.getValue() != null)
                .map(item -> item.getValue().key())
                .toList();

        List<TreeItem<ResultRow>> items = builder.build(List.copyOf(query.getResults()), flavour.get(),
                hiddenFiles, hiddenDetails);
        applyExpansion(items, expansion, expandToggle.isSelected());
        // the selection must not point at rows that are about to leave the tree: sort() re-applies it
        table.getSelectionModel().clearSelection();
        root.getChildren().setAll(items);
        table.sort();

        shownFiles = 0;
        shownMatches = 0;
        count(items);
        updateSummary();
        updateCheckedCount();

        for (String key : selected) {
            TreeItem<ResultRow> item = find(root, key);
            if (item != null) {
                table.getSelectionModel().select(item);
            }
        }
    }

    private void count(List<TreeItem<ResultRow>> items) {
        for (TreeItem<ResultRow> item : items) {
            if (item.getValue() instanceof File file) {
                shownFiles++;
                shownMatches += file.matches().size();
            } else if (item.getValue() instanceof Folder) {
                count(item.getChildren());
            }
        }
    }

    private static void collectExpansion(List<TreeItem<ResultRow>> items, Map<String, Boolean> into) {
        for (TreeItem<ResultRow> item : items) {
            if (!item.getChildren().isEmpty()) {
                into.put(item.getValue().key(), item.isExpanded());
                collectExpansion(item.getChildren(), into);
            }
        }
    }

    private static void applyExpansion(List<TreeItem<ResultRow>> items, Map<String, Boolean> expansion, boolean fallback) {
        for (TreeItem<ResultRow> item : items) {
            if (!item.getChildren().isEmpty()) {
                item.setExpanded(expansion.getOrDefault(item.getValue().key(), fallback));
                applyExpansion(item.getChildren(), expansion, fallback);
            }
        }
    }

    private static void setExpanded(List<TreeItem<ResultRow>> items, boolean expanded) {
        for (TreeItem<ResultRow> item : items) {
            if (!item.getChildren().isEmpty()) {
                item.setExpanded(expanded);
                setExpanded(item.getChildren(), expanded);
            }
        }
    }

    private static TreeItem<ResultRow> find(TreeItem<ResultRow> item, String key) {
        if (item.getValue() != null && key.equals(item.getValue().key())) {
            return item;
        }
        for (TreeItem<ResultRow> child : item.getChildren()) {
            TreeItem<ResultRow> found = find(child, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The summary row: what was found so far, and how the search ended. */
    private void updateSummary() {
        root.setValue(summary());
    }

    Summary summary() {
        SearchCriteria criteria = query.getCriteria();
        State state = query.getState();
        boolean fileNameOnly = criteria.isFileNameOnly();
        String prefix;
        String emphasis = null;
        String suffix = "";
        if (state == State.RUNNING) {
            prefix = fileNameOnly ? message("SUMMARY_FilesSoFar", shownFiles) : message("SUMMARY_SoFar", shownMatches, shownFiles);
        } else if (state == State.FAILED) {
            prefix = message("SUMMARY_Failed");
        } else if (shownFiles == 0) {
            prefix = message("SUMMARY_None");
        } else if (fileNameOnly) {
            prefix = message("SUMMARY_Files", shownFiles);
        } else {
            prefix = message("SUMMARY_FoundPrefix", shownMatches) + " ";
            emphasis = criteria.text().query().replace('\n', ' ');
            suffix = " " + message("SUMMARY_FoundSuffix", shownFiles);
        }
        String replacement = criteria.isReplace() && shownFiles > 0 && state != State.FAILED
                ? criteria.replacement().text().replace('\n', ' ') : null;
        if (state == State.LIMIT_REACHED) {
            FindQuery.Limit limit = query.limitReachedProperty().get();
            suffix += " " + (limit == FindQuery.Limit.MATCHES
                    ? message("SUMMARY_LimitMatches", query.getLimits().matches())
                    : message("SUMMARY_LimitFiles", query.getLimits().files()));
        } else if (state == State.CANCELLED) {
            suffix += " " + message("SUMMARY_Cancelled");
        }
        return new Summary(prefix, emphasis, suffix, replacement, query.getIssues().size() + replaceIssues.size());
    }

    /** The status line: what the search is doing, how it failed, or where and how long it searched. */
    private void updateStatus() {
        State state = query.getState();
        String message = query.messageProperty().get();
        String text;
        switch (state) {
            case RUNNING -> {
                double progress = query.progressProperty().get();
                text = (message == null ? "" : message)
                        + (progress >= 0 && progress < 1 ? " " + Math.round(progress * 100) + "%" : "");
            }
            case FAILED -> text = message == null ? "" : message;
            default -> {
                double seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
                text = replaceStatus != null ? replaceStatus
                        : message("STATUS_Done", query.getCriteria().scope().label(), String.format("%.1f", seconds));
            }
        }
        status.setText(text);
        status.pseudoClassStateChanged(ERROR, state == State.FAILED);
    }

    // -- scope roots ---------------------------------------------------------------------------

    /**
     * The scope root {@code file} is grouped under: the deepest root folder holding it, else its
     * project's root, else its parent.
     */
    private FileObject rootOf(FileObject file) {
        FileObject best = null;
        for (FileObject root : query.getCriteria().scope().roots()) {
            if (root.isFolder() && (root.equals(file) || FileUtil.isParentOf(root, file))
                    && (best == null || FileUtil.isParentOf(best, root))) {
                best = root;
            }
        }
        if (best != null) {
            return best;
        }
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject owner = registry == null ? null : registry.ownerOf(file);
        return owner != null ? owner.getRoot() : file.getParent();
    }

    /** The name of a scope root's row: its project's name, the path of a browsed folder, else its name. */
    private String rootNameOf(FileObject root) {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        if (registry != null) {
            for (OpenProject project : registry.getOpenProjects()) {
                if (project.getRoot().equals(root)) {
                    return project.getDisplayName();
                }
            }
        }
        return query.getCriteria().scope() instanceof SearchScope.Browse ? FileUtil.getFileDisplayName(root) : root.getNameExt();
    }

    // -- selection -----------------------------------------------------------------------------

    private ResultRow selectedRow() {
        TreeItem<ResultRow> item = table.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    /** Opens the selected occurrence or file in the editor; toggles a folder. */
    private void openSelected() {
        TreeItem<ResultRow> item = table.getSelectionModel().getSelectedItem();
        if (item == null || item.getValue() == null) {
            return;
        }
        switch (item.getValue()) {
            case Detail detail -> open(detail.file(), detail.match());
            case File file -> open(file.file(), file.matches().isEmpty() ? null : file.matches().getFirst());
            case Folder _ -> item.setExpanded(!item.isExpanded());
            case Summary _ -> {
            }
        }
    }

    /** Opens {@code file} at {@code match} in the editor; an archive entry, which has no file to edit, in the preview. */
    private void open(FileObject file, TextMatch match) {
        if (FileUtil.getArchiveFile(file) != null) {
            previewVisible.set(true);
            preview.show(file, match == null ? 0 : match.start(), match == null ? 0 : match.end());
            return;
        }
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (contentManager == null) {
            return;
        }
        if (match != null) {
            contentManager.openFile(file, match.start(), match.end());
        } else {
            contentManager.openFile(file, null);
        }
    }

    /** Reveals the row's occurrence, or a file's first one, in the preview; other rows leave the last one on. */
    private void showInPreview(ResultRow row) {
        switch (row) {
            case Detail detail -> preview.show(detail.file(), detail.match().start(), detail.match().end());
            case File file -> {
                TextMatch first = file.matches().isEmpty() ? null : file.matches().get(0);
                preview.show(file.file(), first == null ? 0 : first.start(), first == null ? 0 : first.end());
            }
            case null, default -> {
            }
        }
    }

    /** Whether the preview is shown, for tests. */
    BooleanProperty previewVisibleProperty() {
        return previewVisible;
    }

    /** How this tab looks now, as saved with its search. */
    ViewState viewState() {
        return new ViewState(flavour.get(), previewVisible.get());
    }

    /** Hides the selected rows - NetBeans' {@code HideResultAction} - and updates the counts. */
    private void removeSelected() {
        List<TreeItem<ResultRow>> selected = List.copyOf(table.getSelectionModel().getSelectedItems());
        boolean changed = false;
        for (TreeItem<ResultRow> item : selected) {
            if (item != null) {
                changed |= hide(item);
            }
        }
        if (changed) {
            rebuild();
        }
    }

    private boolean hide(TreeItem<ResultRow> item) {
        switch (item.getValue()) {
            case File file -> {
                return hiddenFiles.add(file.result());
            }
            case Detail detail -> {
                return hiddenDetails.add(detail);
            }
            case Folder _ -> {
                boolean changed = false;
                for (TreeItem<ResultRow> child : List.copyOf(item.getChildren())) {
                    changed |= hide(child);
                }
                return changed;
            }
            case null, default -> {
                return false;
            }
        }
    }

    /**
     * Selects and opens the occurrence {@code delta} positions away from the selected one in tree
     * order, wrapping around; the files themselves for a file-name search.
     */
    private void step(int delta) {
        List<TreeItem<ResultRow>> targets = new ArrayList<>();
        collect(root, targets, query.getCriteria().isFileNameOnly() ? File.class : Detail.class);
        if (targets.isEmpty()) {
            return;
        }
        TreeItem<ResultRow> current = table.getSelectionModel().getSelectedItem();
        int index = targets.indexOf(current);
        if (index < 0 && current != null && current.getValue() instanceof File file && !file.matches().isEmpty()) {
            // from a file row, the next occurrence is its first one
            index = targets.indexOf(current.getChildren().getFirst()) - (delta > 0 ? 1 : 0);
        }
        int target = index < 0
                ? (delta > 0 ? 0 : targets.size() - 1)
                : Math.floorMod(index + delta, targets.size());
        TreeItem<ResultRow> item = targets.get(target);
        for (TreeItem<ResultRow> parent = item.getParent(); parent != null; parent = parent.getParent()) {
            parent.setExpanded(true);
        }
        table.getSelectionModel().clearAndSelect(table.getRow(item));
        table.scrollTo(table.getRow(item));
        openSelected();
    }

    private static void collect(TreeItem<ResultRow> item, List<TreeItem<ResultRow>> into, Class<? extends ResultRow> kind) {
        if (kind.isInstance(item.getValue())) {
            into.add(item);
        }
        for (TreeItem<ResultRow> child : item.getChildren()) {
            collect(child, into, kind);
        }
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(SearchResultsPane.class, key, args);
    }
}

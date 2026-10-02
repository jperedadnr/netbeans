package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FindModel;
import com.gluonhq.netbeans.nbfx.findinprojects.query.FindQuery;
import com.gluonhq.netbeans.nbfx.findinprojects.query.FindQuery.State;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.SavedSearch.ViewState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The content of the "Search" tab - NetBeans' {@code ResultView}: one inner tab per query of the
 * {@link FindModel}, titled with the search text. A search started with "Open in New Tab" off
 * replaces the previous such query in the model, so its tab is replaced in place and stays where
 * it was. The inner tab header is hidden (pseudo-class {@code single}) while there is one query,
 * and a hint is shown while there is none.
 * <p>
 * The open searches are saved whenever a tab is added, removed, reordered, searches again or
 * changes its look, and run again at the next start by {@link SearchRestorer} once the view is
 * recreated by the layout.
 */
final class SearchView extends StackPane {

    private static final PseudoClass SINGLE = PseudoClass.getPseudoClass("single");
    /** Longer tab titles are cut with an ellipsis. */
    static final int TITLE_LIMIT = 40;

    private final FindModel model;
    private final FindSettings settings;
    private final TabPane queries = new TabPane();
    private final Label empty = new Label(message("LBL_NoResults"));
    /** The saved look of the searches being restored, by the criteria they were started with. */
    private final Map<SearchCriteria, ViewState> savedViews = new IdentityHashMap<>();

    SearchView(FindModel model) {
        this(model, FindSettings.getDefault());
    }

    SearchView(FindModel model, FindSettings settings) {
        this(model, settings, Lookup.getDefault().lookup(ProjectRegistry.class));
    }

    /**
     * @param registry the open projects, which the restored searches wait for and whose closing
     *                 drops the searches under them; {@code null} when there is none (tests),
     *                 when the restored searches run over the roots they were saved with
     */
    SearchView(FindModel model, FindSettings settings, ProjectRegistry registry) {
        this.model = Objects.requireNonNull(model);
        this.settings = Objects.requireNonNull(settings);
        getStyleClass().add("search-view");
        getStylesheets().add(Objects.requireNonNull(SearchView.class.getResource("search.css")).toExternalForm());

        queries.getStyleClass().add("nbfx-tab-pane");
        queries.pseudoClassStateChanged(PseudoClass.getPseudoClass("search-results"), true);
        queries.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        queries.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        empty.getStyleClass().add("search-empty");
        StackPane.setAlignment(empty, Pos.CENTER);
        getChildren().addAll(empty, queries);

        model.getQueries().forEach(query -> queries.getTabs().add(createTab(query)));
        model.getQueries().addListener((ListChangeListener<FindQuery>) change -> {
            while (change.next()) {
                if (change.wasReplaced()) {
                    List<? extends FindQuery> removed = change.getRemoved();
                    List<? extends FindQuery> added = change.getAddedSubList();
                    for (int i = 0; i < removed.size(); i++) {
                        replace(removed.get(i), i < added.size() ? added.get(i) : null);
                    }
                    for (int i = removed.size(); i < added.size(); i++) {
                        add(added.get(i));
                    }
                    continue;
                }
                for (FindQuery removed : change.getRemoved()) {
                    queries.getTabs().removeIf(tab -> tab.getUserData() == removed);
                }
                for (FindQuery added : change.getAddedSubList()) {
                    add(added);
                }
            }
        });
        queries.getTabs().addListener((ListChangeListener<Tab>) _ -> updateHeader());
        updateHeader();

        // The view is created once per session, by the layout or the first search: with no query
        // yet, last session's searches are run again, each tab picking up its saved look, then
        // the saved list is kept in step with the open tabs.
        if (model.getQueries().isEmpty()) {
            SearchRestorer.restore(settings.getSearches(), registry, (saved, criteria) -> {
                if (saved.view() != null) {
                    savedViews.put(criteria, saved.view());
                }
                model.find(criteria, !saved.reusable());
            });
        }
        queries.getTabs().addListener((ListChangeListener<Tab>) _ -> save());

        // Closing a project closes its editors: its searches go with them. The view lives as long
        // as the session, so the listener is never removed.
        if (registry != null) {
            registry.getOpenProjects().addListener((ListChangeListener<OpenProject>) change -> {
                while (change.next()) {
                    for (OpenProject closed : change.getRemoved()) {
                        model.removeDependingOn(closed.getRoot());
                    }
                }
            });
        }
    }

    private void save() {
        settings.setSearches(queries.getTabs().stream()
                .map(tab -> (SearchResultsPane) tab.getContent())
                .map(pane -> SavedSearch.of(pane.getQuery().getCriteria(),
                        pane.getQuery() == model.getReusable(), pane.viewState()))
                .toList());
    }

    /** Puts the tab of {@code next} where the tab of {@code previous} is, keeping its selection. */
    private void replace(FindQuery previous, FindQuery next) {
        Tab old = tabOf(previous);
        if (old == null) {
            if (next != null) {
                add(next);
            }
            return;
        }
        if (next == null) {
            queries.getTabs().remove(old);
            return;
        }
        boolean selected = old.isSelected();
        Tab tab = createTab(next);
        queries.getTabs().set(queries.getTabs().indexOf(old), tab);
        if (selected) {
            queries.getSelectionModel().select(tab);
        }
    }

    private void add(FindQuery query) {
        if (tabOf(query) == null) {
            Tab tab = createTab(query);
            queries.getTabs().add(tab);
            queries.getSelectionModel().select(tab);
        }
    }

    /** Selects the tab of {@code query} and focuses its table; a no-op for an unknown query. */
    void select(FindQuery query) {
        Tab tab = tabOf(query);
        if (tab != null) {
            queries.getSelectionModel().select(tab);
            ((SearchResultsPane) tab.getContent()).requestTableFocus();
        }
    }

    /** Removes (and cancels) every query. */
    void closeAll() {
        model.removeAll();
    }

    /** The inner tab pane, for tests. */
    TabPane tabs() {
        return queries;
    }

    private Tab createTab(FindQuery query) {
        SearchResultsPane pane = new SearchResultsPane(query, model, settings, savedViews.remove(query.getCriteria()));
        Tab tab = new Tab(tabTitle(query.getCriteria()), pane);
        tab.setUserData(query);
        tab.setGraphic(SearchIcons.view("find"));
        // Modify Criteria searches again in the same tab: the title and the saved search follow
        // the new criteria (the tab is not in the pane yet the first time: the tabs listener saves).
        query.stateProperty().subscribe(state -> {
            if (state == State.RUNNING) {
                tab.setText(tabTitle(query.getCriteria()));
                if (queries.getTabs().contains(tab)) {
                    save();
                }
            }
        });
        pane.flavourProperty().subscribe((was, now) -> save());
        pane.previewVisibleProperty().subscribe((was, now) -> save());
        // Closing the tab drops the query; the model listener above then removes nothing more.
        tab.setOnClosed(e -> model.remove(query));
        tab.setContextMenu(createTabMenu(tab));
        return tab;
    }

    /** NetBeans' tab menu: Close Tab, Close All Tabs, Close Other Tabs. */
    private ContextMenu createTabMenu(Tab tab) {
        MenuItem close = new MenuItem(message("CTX_CloseTab"));
        close.setOnAction(e -> model.remove((FindQuery) tab.getUserData()));
        MenuItem closeAll = new MenuItem(message("CTX_CloseAllTabs"));
        closeAll.setOnAction(e -> closeAll());
        MenuItem closeOthers = new MenuItem(message("CTX_CloseOtherTabs"));
        closeOthers.setOnAction(e -> {
            for (FindQuery query : List.copyOf(model.getQueries())) {
                if (query != tab.getUserData()) {
                    model.remove(query);
                }
            }
        });
        return new ContextMenu(close, closeAll, closeOthers);
    }

    /**
     * NetBeans' tab title: the search text in quotes, on one line and cut at {@link #TITLE_LIMIT}
     * characters; {@code "*"} for a file-name search.
     */
    static String tabTitle(SearchCriteria criteria) {
        if (criteria.isFileNameOnly()) {
            return message("TAB_FilePattern");
        }
        String text = criteria.text().query().replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        if (text.length() > TITLE_LIMIT) {
            text = text.substring(0, TITLE_LIMIT - 1) + "…";
        }
        return message("TAB_Text", text);
    }

    private Tab tabOf(FindQuery query) {
        return queries.getTabs().stream()
                .filter(tab -> tab.getUserData() == query)
                .findFirst()
                .orElse(null);
    }

    private void updateHeader() {
        int count = queries.getTabs().size();
        queries.pseudoClassStateChanged(SINGLE, count == 1);
        queries.setVisible(count > 0);
        empty.setVisible(count == 0);
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(SearchView.class, key, args);
    }
}

package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.findusages.model.UsagesModel;
import com.gluonhq.netbeans.nbfx.findusages.query.UsagesQuery;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsagesSettings.SavedQuery;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsagesSettings.ViewState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import org.openide.util.Lookup;

/**
 * The content of the "Usages" tab: one inner tab per open query, titled "Usages of X". The inner
 * tab header is hidden (pseudo-class {@code single}) while there is one query, and a hint is shown
 * while there is none.
 */
final class UsagesView extends StackPane {

    private static final PseudoClass SINGLE = PseudoClass.getPseudoClass("single");

    private final UsagesModel model;
    private final UsagesSettings settings;
    private final TabPane queries = new TabPane();
    /** The saved look of last session's tabs, by query key, handed to each tab as it is restored. */
    private final Map<String, ViewState> savedViews = new HashMap<>();
    private final Label empty = new Label(UsagesQueryPane.message("LBL_NoUsages"));

    UsagesView(UsagesModel model) {
        this(model, new UsagesSettings());
    }

    UsagesView(UsagesModel model, UsagesSettings settings) {
        this(model, settings, Lookup.getDefault().lookup(ProjectRegistry.class));
    }

    /** @param registry the open projects, whose closing drops the queries under them; {@code null} for none (tests) */
    UsagesView(UsagesModel model, UsagesSettings settings, ProjectRegistry registry) {
        this.model = model;
        this.settings = settings;
        getStyleClass().add("usages-view");
        getStylesheets().add(Objects.requireNonNull(UsagesView.class.getResource("usages.css")).toExternalForm());

        queries.getStyleClass().add("nbfx-tab-pane");
        queries.pseudoClassStateChanged(PseudoClass.getPseudoClass("usages-queries"), true);
        queries.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        queries.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        empty.getStyleClass().add("usages-empty");
        StackPane.setAlignment(empty, Pos.CENTER);
        getChildren().addAll(empty, queries);

        model.getQueries().forEach(query -> queries.getTabs().add(createTab(query)));
        model.getQueries().addListener((ListChangeListener<UsagesQuery>) change -> {
            while (change.next()) {
                for (UsagesQuery removed : change.getRemoved()) {
                    queries.getTabs().removeIf(tab -> tab.getUserData() == removed);
                }
                for (UsagesQuery added : change.getAddedSubList()) {
                    if (tabOf(added) == null) {
                        Tab tab = createTab(added);
                        queries.getTabs().add(tab);
                        queries.getSelectionModel().select(tab);
                    }
                }
            }
        });
        queries.getTabs().addListener((ListChangeListener<Tab>) change -> updateHeader());
        updateHeader();

        // New queries start with the options the user chose last.
        model.setDefaultOptions(settings.getOptions());

        // The view is created when the layout brings the Usages tab back: run last session's queries
        // again, each tab picking up its saved look, then keep the saved list - in tab order, with
        // every tab's look - in step with the open ones.
        if (model.getQueries().isEmpty()) {
            List<SavedQuery> saved = settings.getQueries();
            saved.forEach(entry -> savedViews.putIfAbsent(entry.key(), entry.view()));
            UsagesRestorer.restore(model, saved);
        }
        queries.getTabs().addListener((ListChangeListener<Tab>) change -> save());

        // Closing a project closes its editors: the queries of its elements go with them. The view
        // lives as long as the session, so the listener is never removed.
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
        settings.setQueries(queries.getTabs().stream()
                .map(tab -> (UsagesQueryPane) tab.getContent())
                .map(pane -> new SavedQuery(pane.getQuery(), pane.viewStateProperty().get()))
                .toList());
    }

    /** Selects the tab of {@code query} and focuses its tree; a no-op for an unknown query. */
    void select(UsagesQuery query) {
        Tab tab = tabOf(query);
        if (tab != null) {
            queries.getSelectionModel().select(tab);
            ((UsagesQueryPane) tab.getContent()).requestTreeFocus();
        }
    }

    /** Removes (and cancels) every query. */
    void closeAll() {
        for (UsagesQuery query : List.copyOf(model.getQueries())) {
            model.remove(query);
        }
    }

    private Tab createTab(UsagesQuery query) {
        ViewState view = savedViews.remove(new SavedQuery(query, null).key());
        UsagesQueryPane pane = new UsagesQueryPane(query, settings, view);
        pane.viewStateProperty().subscribe((was, now) -> save());
        Tab tab = new Tab(UsagesQueryPane.message("TAB_UsagesOf", pane.getTargetName()), pane);
        tab.setUserData(query);
        tab.setGraphic(UsagesIcons.view("findusages"));
        // Closing the tab drops the query; the model listener above then removes nothing more.
        tab.setOnClosed(e -> model.remove(query));
        return tab;
    }

    private Tab tabOf(UsagesQuery query) {
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
}

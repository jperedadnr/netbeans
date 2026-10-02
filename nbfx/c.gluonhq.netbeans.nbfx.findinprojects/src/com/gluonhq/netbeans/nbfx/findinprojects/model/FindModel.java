package com.gluonhq.netbeans.nbfx.findinprojects.model;

import com.gluonhq.netbeans.nbfx.findinprojects.query.FindQuery;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import java.util.List;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 * The searches the Search view shows, shared by the Find in Projects dialog that starts them and
 * the view that displays them. Registered in the default Lookup; used on the JavaFX thread.
 * <p>
 * Tab reuse is decided here, as NetBeans' {@code ResultView.markCurrentTabAsReusable} /
 * {@code tryReuse}: a search started with "Open in New Tab" off replaces the last query started
 * that way - in place, so its tab keeps its position - instead of adding one.
 */
@ServiceProvider(service = FindModel.class)
public final class FindModel {

    private final ObservableList<FindQuery> queries = FXCollections.observableArrayList();
    private FindQuery reusable;
    private SearchCriteria lastCriteria;

    /** The open queries, oldest first. */
    public ObservableList<FindQuery> getQueries() {
        return queries;
    }

    /**
     * Starts a search for {@code criteria} and adds it to {@link #getQueries()} - or, when
     * {@code openInNewTab} is off and the previous such search is still open, puts it in that one's
     * place. Searches opened in a new tab are never replaced and leave the reusable one as it is.
     *
     * @return the new query
     */
    public FindQuery find(SearchCriteria criteria, boolean openInNewTab) {
        Objects.requireNonNull(criteria);
        lastCriteria = criteria;
        FindQuery query = FindQuery.start(criteria);
        int index = -1;
        if (!openInNewTab && reusable != null) {
            index = queries.indexOf(reusable);
            if (index >= 0) {
                reusable.cancel();
            }
        }
        if (index >= 0) {
            queries.set(index, query);
        } else {
            queries.add(query);
        }
        if (!openInNewTab) {
            reusable = query;
        }
        return query;
    }

    /** Cancels and drops {@code query}. */
    public void remove(FindQuery query) {
        if (queries.remove(query)) {
            query.cancel();
        }
        if (reusable == query) {
            reusable = null;
        }
    }

    /**
     * Cancels and drops every query that searched under {@code projectRoot} - a scope root that is
     * the project or lies inside it - as the editors of a closed project are closed; a query over
     * several projects goes with the first of them to close.
     */
    public void removeDependingOn(FileObject projectRoot) {
        Objects.requireNonNull(projectRoot);
        for (FindQuery query : List.copyOf(queries)) {
            if (query.getCriteria().scope().roots().stream()
                    .anyMatch(root -> root.equals(projectRoot) || FileUtil.isParentOf(projectRoot, root))) {
                remove(query);
            }
        }
    }

    /** Cancels and drops every query. */
    public void removeAll() {
        for (FindQuery query : queries) {
            query.cancel();
        }
        queries.clear();
        reusable = null;
    }

    /** The query a search with "Open in New Tab" off would replace, or {@code null}. */
    public FindQuery getReusable() {
        return reusable;
    }

    /** The criteria of the last search started, to seed the dialog; {@code null} before the first. */
    public SearchCriteria getLastCriteria() {
        return lastCriteria;
    }

    public void setLastCriteria(SearchCriteria criteria) {
        lastCriteria = criteria;
    }
}

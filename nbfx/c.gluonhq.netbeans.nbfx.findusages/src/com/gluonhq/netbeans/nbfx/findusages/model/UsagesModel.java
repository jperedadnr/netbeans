package com.gluonhq.netbeans.nbfx.findusages.model;

import com.gluonhq.netbeans.nbfx.findusages.query.SourceSet;
import com.gluonhq.netbeans.nbfx.findusages.query.UsagesQuery;
import com.gluonhq.netbeans.nbfx.findusages.query.CssTarget;
import com.gluonhq.netbeans.nbfx.findusages.query.QueryOptions;
import com.gluonhq.netbeans.nbfx.findusages.query.SearchTarget;
import com.gluonhq.netbeans.nbfx.findusages.query.UsagesTarget;
import java.io.IOException;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * The queries the Usages view shows, shared by the Find Usages command that starts them and the
 * view that displays them. Registered in the default Lookup.
 */
@ServiceProvider(service = UsagesModel.class)
public final class UsagesModel {

    private static final Logger LOG = Logger.getLogger(UsagesModel.class.getName());
    private static final RequestProcessor RESOLVER = new RequestProcessor("Find Usages target", 1, true);

    private final ObservableList<UsagesQuery> queries = FXCollections.observableArrayList();

    /** The open queries, oldest first; modified on the JavaFX thread. */
    private volatile QueryOptions defaultOptions = QueryOptions.DEFAULT;

    public ObservableList<UsagesQuery> getQueries() {
        return queries;
    }

    /**
     * Resolves the element at {@code offset} of {@code file} (in the editor's {@code '\n'}-counted
     * offsets) in the background - a Java element, or a class / id selector or colour when
     * {@code file} is a style sheet - and, when there is one, starts a query for it over the
     * sources of all open projects and adds it to {@link #getQueries()}.
     *
     * @return the new query, or {@code null} when there is no searchable element at the caret
     */
    public CompletableFuture<UsagesQuery> findUsages(FileObject file, int offset) {
        return findUsages(file, offset, defaultOptions);
    }

    /** {@link #findUsages(FileObject, int)} with the given options rather than the {@link #getDefaultOptions() defaults}. */
    public CompletableFuture<UsagesQuery> findUsages(FileObject file, int offset, QueryOptions options) {
        return start(file, options, sources -> CssTarget.isStyleSheet(file)
                ? CssTarget.at(file, offset, sources)
                : UsagesTarget.at(file, offset, sources));
    }

    /**
     * Like {@link #findUsages(FileObject, int)} for the top-level type of {@code file} (the one
     * named after it): Find Usages on a file node of the Projects / Files views.
     *
     * @return the new query, or {@code null} when the file declares no type
     */
    public CompletableFuture<UsagesQuery> findUsagesOfType(FileObject file) {
        return start(file, defaultOptions, sources -> UsagesTarget.topLevelTypeOf(file, sources));
    }

    /** The options new queries start with; the view keeps them at the last ones the user chose. */
    public QueryOptions getDefaultOptions() {
        return defaultOptions;
    }

    public void setDefaultOptions(QueryOptions options) {
        this.defaultOptions = Objects.requireNonNull(options);
    }

    private interface TargetResolver {
        SearchTarget resolve(SourceSet sources) throws IOException;
    }

    private CompletableFuture<UsagesQuery> start(FileObject file, QueryOptions options, TargetResolver resolver) {
        CompletableFuture<UsagesQuery> result = new CompletableFuture<>();
        // The sources are described on the caller's thread: the open-files scope asks the UI.
        SourceSet sources = SourceSet.forScope(options.scope(), file);
        RESOLVER.post(() -> {
            try {
                SearchTarget target = resolver.resolve(sources);
                if (target == null) {
                    result.complete(null);
                    return;
                }
                UsagesQuery query = UsagesQuery.start(target, scope -> SourceSet.forScope(scope, file), options);
                onFxThread(() -> {
                    queries.add(query);
                    result.complete(query);
                });
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Could not resolve the element to find usages of", ex);
                result.completeExceptionally(ex);
            }
        });
        return result;
    }

    /**
     * Closes every query whose target lives under {@code projectRoot}, as the editors of a closed
     * project are closed: the element is gone with its project. Call on the JavaFX thread.
     */
    public void removeDependingOn(FileObject projectRoot) {
        for (UsagesQuery query : List.copyOf(queries)) {
            FileObject file = query.getTarget().getFile();
            if (file != null && (file.equals(projectRoot) || FileUtil.isParentOf(projectRoot, file))) {
                remove(query);
            }
        }
    }

    /** Closes {@code query}: cancels it and drops it from the list. */
    public void remove(UsagesQuery query) {
        query.cancel();
        onFxThread(() -> queries.remove(query));
    }

    private static void onFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException toolkitNotRunning) {
            action.run();
        }
    }
}

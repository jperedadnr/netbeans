package com.gluonhq.netbeans.nbfx.findusages.query;

import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * One Find Usages run: resolves nothing itself - it is given a {@link SearchTarget} - and, on a
 * background thread, drives the target's {@link SearchTarget.Search search} (javac-confirmed
 * occurrences for a Java element, a selector scan of the style sheets for a CSS one). Results and
 * state are published on the JavaFX thread (or directly when no toolkit runs, as in tests), so the
 * view can bind to them while the query runs.
 * <p>
 * {@link #cancel()} stops the run and keeps what was found; {@link #refresh()} runs it again over
 * the current sources, reporting when the element no longer exists, as NetBeans does;
 * {@link #rerun} does so with other {@link QueryOptions} and sources.
 */
public final class UsagesQuery {

    public enum State {
        RUNNING, DONE, CANCELLED, FAILED
    }

    private static final Logger LOG = Logger.getLogger(UsagesQuery.class.getName());
    private static final RequestProcessor PROCESSOR = new RequestProcessor("Find Usages", 2, true);

    private final SearchTarget target;
    private volatile SourceSet sources;
    private volatile QueryOptions options;
    /** Describes the sources of a scope anew at every run, so a refresh sees the tabs open now; {@code null} keeps {@link #sources}. */
    private volatile Function<QueryOptions.Scope, SourceSet> sourcesFactory;
    private volatile SearchTarget.Search search;
    private final ObservableList<Usage> usages = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.RUNNING);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper();
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(-1);

    private volatile Run run;

    private UsagesQuery(SearchTarget target, SourceSet sources, QueryOptions options) {
        this.target = Objects.requireNonNull(target);
        this.sources = Objects.requireNonNull(sources);
        this.options = Objects.requireNonNull(options);
    }

    /** Starts a query for {@code target} over {@code sources}; it is already running when returned. */
    public static UsagesQuery start(SearchTarget target, SourceSet sources, QueryOptions options) {
        UsagesQuery query = new UsagesQuery(target, sources, options);
        query.launch();
        return query;
    }

    /**
     * Starts a query whose sources are described by {@code sourcesFactory} for the options' scope,
     * now and at every {@link #refresh()} or {@link #rerun(QueryOptions)} - the factory is called
     * on the caller's thread, so the open-files scope can ask the UI.
     */
    public static UsagesQuery start(SearchTarget target, Function<QueryOptions.Scope, SourceSet> sourcesFactory,
            QueryOptions options) {
        UsagesQuery query = new UsagesQuery(target, sourcesFactory.apply(options.scope()), options);
        query.sourcesFactory = sourcesFactory;
        query.launch();
        return query;
    }

    /** {@link #start(SearchTarget, SourceSet, QueryOptions)} over all projects. */
    public static UsagesQuery start(SearchTarget target, SourceSet sources, boolean searchComments) {
        return start(target, sources, QueryOptions.DEFAULT.withSearchComments(searchComments));
    }

    public SearchTarget getTarget() {
        return target;
    }

    public SourceSet getSources() {
        return sources;
    }

    public QueryOptions getOptions() {
        return options;
    }

    /** The usages found so far, in the order they were confirmed (the target's own file first). */
    public ObservableList<Usage> getUsages() {
        return usages;
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public State getState() {
        return state.get();
    }

    /** Why the query {@link State#FAILED failed}, {@code null} otherwise. */
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /** Fraction of the candidate files scanned, or -1 while they are being collected. */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    /** Completes when the current run ends, whatever its outcome. */
    public CompletableFuture<State> whenDone() {
        return run.done;
    }

    /** Stops the running search; the usages found so far stay. */
    public void cancel() {
        Run current = run;
        if (current != null) {
            current.cancelled.set(true);
        }
    }

    /** Cancels any running search and searches again over the sources the scope names now. */
    public void refresh() {
        rerun(options);
    }

    /** Cancels any running search and searches again with {@code options}, over the sources its scope names now. */
    public void rerun(QueryOptions options) {
        Function<QueryOptions.Scope, SourceSet> factory = sourcesFactory;
        rerun(options, factory == null ? sources : factory.apply(options.scope()));
    }

    /** Cancels any running search and searches again with {@code options} over {@code sources}. */
    public void rerun(QueryOptions options, SourceSet sources) {
        cancel();
        this.options = Objects.requireNonNull(options);
        this.sources = Objects.requireNonNull(sources);
        launch();
    }

    private void launch() {
        search = target.newSearch(sources, options.searchComments());
        Run next = new Run();
        run = next;
        publish(() -> {
            usages.clear();
            message.set(null);
            progress.set(-1);
            state.set(State.RUNNING);
        });
        PROCESSOR.post(() -> execute(next));
    }

    private void execute(Run current) {
        SearchTarget.Search search = this.search;
        try {
            if (!search.targetExists()) {
                finish(current, State.FAILED, NbBundle.getMessage(UsagesQuery.class, "MSG_ElementNotAvailable"));
                return;
            }
            search.run(current.cancelled::get, found -> {
                List<Usage> batch = new ArrayList<>(found);
                publish(() -> {
                    if (run == current) {
                        usages.addAll(batch);
                    }
                });
            }, fraction -> publish(() -> {
                if (run == current) {
                    progress.set(fraction);
                }
            }));
            finish(current, current.cancelled.get() ? State.CANCELLED : State.DONE, null);
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Find Usages of " + target + " failed", ex);
            finish(current, State.FAILED, ex.getLocalizedMessage());
        }
    }

    private void finish(Run current, State outcome, String text) {
        publish(() -> {
            if (run == current) {
                message.set(text);
                progress.set(1);
                state.set(outcome);
            }
            current.done.complete(outcome);
        });
    }

    /** Runs {@code action} on the JavaFX thread when there is one, else right here. */
    private static void publish(Runnable action) {
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

    /** One execution of the query, so a refresh can supersede a run that is still finishing. */
    private static final class Run {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final CompletableFuture<State> done = new CompletableFuture<>();
    }
}

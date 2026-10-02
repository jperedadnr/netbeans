package com.gluonhq.netbeans.nbfx.findinprojects.query;

import com.gluonhq.netbeans.nbfx.api.search.TextSearch;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FileResult;
import com.gluonhq.netbeans.nbfx.findinprojects.model.Issue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * One Find in Projects run - NetBeans' {@code SearchTask} driven by {@code Manager}: lists the
 * files of the scope ({@link FileWalker}), then searches them ({@link FileMatcher}) on a background
 * thread, one query at a time. Results, issues, state and progress are published on the JavaFX
 * thread (or directly when no toolkit runs, as in tests), so the view can bind to them while the
 * query runs.
 * <p>
 * The search stops - {@link State#LIMIT_REACHED} - at NetBeans' limits: {@link Limits#files()}
 * matching files or {@link Limits#matches()} occurrences. {@link #cancel()} stops it and keeps what
 * was found; {@link #rerun()} searches again over the scope as it is now.
 */
public final class FindQuery {

    public enum State {
        RUNNING, DONE, CANCELLED, FAILED, LIMIT_REACHED
    }

    /** Which limit stopped the search. */
    public enum Limit {
        FILES, MATCHES
    }

    /**
     * NetBeans' {@code Constants}: at most {@code files} matching files (system property
     * {@code netbeans.search.count.limit}, 500) and {@code matches} occurrences
     * ({@code netbeans.search.details.count.limit}, 5000).
     */
    public record Limits(int files, int matches) {

        public static final Limits DEFAULT = new Limits(
                Integer.getInteger("netbeans.search.count.limit", 500),
                Integer.getInteger("netbeans.search.details.count.limit", 5000));
    }

    private static final Logger LOG = Logger.getLogger(FindQuery.class.getName());
    /** One search at a time, as NetBeans' {@code Manager} queues them. */
    private static final RequestProcessor PROCESSOR = new RequestProcessor("Find in Projects", 1, true);
    /** Progress is published every so many files, not on each one. */
    private static final int PROGRESS_STEP = 25;

    private final FileMatcher matcher;
    private final IgnoreList ignoreList;
    private final Limits limits;
    private volatile SearchCriteria criteria;

    private final ObservableList<FileResult> results = FXCollections.observableArrayList();
    private final ObservableList<Issue> issues = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.RUNNING);
    private final ReadOnlyObjectWrapper<Limit> limitReached = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper();
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(-1);
    private final ReadOnlyIntegerWrapper matchCount = new ReadOnlyIntegerWrapper();

    private volatile Run run;

    private FindQuery(SearchCriteria criteria, FileMatcher matcher, IgnoreList ignoreList, Limits limits) {
        this.criteria = Objects.requireNonNull(criteria);
        this.matcher = Objects.requireNonNull(matcher);
        this.ignoreList = Objects.requireNonNull(ignoreList);
        this.limits = Objects.requireNonNull(limits);
    }

    /** Starts a search for {@code criteria} with the shared matcher, ignore list and limits; it is already running when returned. */
    public static FindQuery start(SearchCriteria criteria) {
        return start(criteria, new FileMatcher(), IgnoreList.getDefault(), Limits.DEFAULT);
    }

    /** Starts a search with the given collaborators (tests). */
    public static FindQuery start(SearchCriteria criteria, FileMatcher matcher, IgnoreList ignoreList, Limits limits) {
        FindQuery query = new FindQuery(criteria, matcher, ignoreList, limits);
        query.launch();
        return query;
    }

    /** What is searched; changes with {@link #rerun(SearchCriteria)}. */
    public SearchCriteria getCriteria() {
        return criteria;
    }

    /** The limits that stop this search. */
    public Limits getLimits() {
        return limits;
    }

    /** The matching files found so far, in walk order. */
    public ObservableList<FileResult> getResults() {
        return results;
    }

    /** The files that could not be searched. */
    public ObservableList<Issue> getIssues() {
        return issues;
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public State getState() {
        return state.get();
    }

    /** The limit that stopped the search, {@code null} unless {@link State#LIMIT_REACHED}. */
    public ReadOnlyObjectProperty<Limit> limitReachedProperty() {
        return limitReached.getReadOnlyProperty();
    }

    /** What the search is doing, or why it {@link State#FAILED failed}. */
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /** Fraction of the files searched, or -1 while they are being listed. */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    /** The occurrences found so far, over every result. */
    public ReadOnlyIntegerProperty matchCountProperty() {
        return matchCount.getReadOnlyProperty();
    }

    public int getMatchCount() {
        return matchCount.get();
    }

    /** Completes when the current run ends, whatever its outcome. */
    public CompletableFuture<State> whenDone() {
        return run.done;
    }

    /** Stops the running search; the results found so far stay. */
    public void cancel() {
        Run current = run;
        if (current != null) {
            current.cancelled.set(true);
        }
    }

    /** Cancels any running search and searches again over the scope as it is now (call on the JavaFX thread). */
    public void rerun() {
        rerun(criteria.withScope(criteria.scope().refreshed()));
    }

    /** Cancels any running search and searches again for {@code criteria}. */
    public void rerun(SearchCriteria criteria) {
        cancel();
        this.criteria = Objects.requireNonNull(criteria);
        launch();
    }

    private void launch() {
        Run next = new Run();
        run = next;
        publish(() -> {
            results.clear();
            issues.clear();
            matchCount.set(0);
            limitReached.set(null);
            message.set(message("MSG_Preparing"));
            progress.set(-1);
            state.set(State.RUNNING);
        });
        PROCESSOR.post(() -> execute(next));
    }

    private void execute(Run current) {
        SearchCriteria what = criteria;
        try {
            TextSearch search = what.isFileNameOnly() ? null : what.text().compile();
            Predicate<FileObject> nameFilter = what.fileName().compile();
            List<FileObject> files = new FileWalker(what.options(), ignoreList)
                    .collect(what.scope(), current.cancelled::get);
            if (current.cancelled.get()) {
                finish(current, State.CANCELLED, null);
                return;
            }
            publish(() -> {
                if (run == current) {
                    message.set(message("MSG_Searching"));
                    progress.set(files.isEmpty() ? 1 : 0);
                }
            });
            int fileCount = 0;
            int total = 0;
            Limit limit = null;
            for (int i = 0; i < files.size() && !current.cancelled.get(); i++) {
                FileObject file = files.get(i);
                if (nameFilter.test(file)) {
                    FileResult result = searchFile(current, search, file);
                    if (result != null) {
                        fileCount++;
                        total += result.matchCount();
                        int found = total;
                        publish(() -> {
                            if (run == current) {
                                results.add(result);
                                matchCount.set(found);
                            }
                        });
                        if (fileCount >= limits.files()) {
                            limit = Limit.FILES;
                            break;
                        }
                        if (search != null && total >= limits.matches()) {
                            limit = Limit.MATCHES;
                            break;
                        }
                    }
                }
                if (i % PROGRESS_STEP == PROGRESS_STEP - 1) {
                    double fraction = (i + 1) / (double) files.size();
                    publish(() -> {
                        if (run == current) {
                            progress.set(fraction);
                        }
                    });
                }
            }
            if (current.cancelled.get()) {
                finish(current, State.CANCELLED, null);
            } else if (limit != null) {
                Limit reached = limit;
                publish(() -> {
                    if (run == current) {
                        limitReached.set(reached);
                    }
                });
                finish(current, State.LIMIT_REACHED, null);
            } else {
                finish(current, State.DONE, null);
            }
        } catch (RuntimeException | LinkageError ex) {
            LOG.log(Level.WARNING, "Find in Projects for " + what.text().query() + " failed", ex);
            finish(current, State.FAILED, String.valueOf(ex.getLocalizedMessage()));
        }
    }

    /** The result for one file, {@code null} when it does not match or cannot be searched (an issue then). */
    private FileResult searchFile(Run current, TextSearch search, FileObject file) {
        if (search == null) {
            return FileMatcher.nameOnly(file);
        }
        if (FileMatcher.isTooLarge(file)) {
            report(current, FileMatcher.tooLargeIssue(file));
            return null;
        }
        if (!FileMatcher.isSearchable(file)) {
            return null;
        }
        try {
            return matcher.match(search, file);
        } catch (IOException ex) {
            report(current, FileMatcher.readIssue(file, ex));
            return null;
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Could not search " + file.getPath(), ex);
            report(current, new Issue(file.getPath(), message("ISSUE_Read", file.getNameExt(), String.valueOf(ex))));
            return null;
        }
    }

    private void report(Run current, Issue issue) {
        publish(() -> {
            if (run == current) {
                issues.add(issue);
            }
        });
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

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(FindQuery.class, key, args);
    }

    /** One execution of the query, so a rerun can supersede a run that is still finishing. */
    private static final class Run {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final CompletableFuture<State> done = new CompletableFuture<>();
    }
}

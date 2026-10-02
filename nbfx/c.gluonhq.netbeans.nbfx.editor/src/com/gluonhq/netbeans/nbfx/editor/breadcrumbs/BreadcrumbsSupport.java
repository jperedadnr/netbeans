package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.editor.EditorSettings;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsContext;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsProvider;
import com.gluonhq.netbeans.nbfx.editor.processor.SourceUtils;
import javafx.application.Platform;
import javafx.util.Subscription;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives a {@link BreadcrumbsBar} from the caret of a {@link CodeArea}: caret moves are debounced,
 * the first registered {@link BreadcrumbsProvider} that supports the file computes the deepest
 * element enclosing the caret off the FX thread, and the resulting
 * {@linkplain BreadcrumbElement#path() path} is published back to the bar on the FX thread.
 *
 * <p>No work is scheduled while the shared "show breadcrumbs" setting is off; turning it back on
 * refreshes the bar for the current caret position. The document snapshot handed to providers is
 * cached and only re-extracted from the model after an edit (mirroring the invalidation pattern
 * of {@code JavaSourceContext}), so plain caret moves stay cheap even for long files. Stale queries
 * are cancelled through the request sequence, exposed to providers as a
 * {@link com.gluonhq.netbeans.nbfx.api.Cancellation}. Left-clicking a crumb
 * moves the caret to the element's start offset.</p>
 */
public final class BreadcrumbsSupport {

    private static final Logger LOG = Logger.getLogger(BreadcrumbsSupport.class.getName());

    /** Delay (ms) after the last caret move before the breadcrumbs query is started. */
    // Package-private for testing
    static final long BREADCRUMBS_DELAY_MS = 300;

    /** Debounces the breadcrumbs queries of every open editor; the queries themselves run async. */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "breadcrumbs-delay");
                t.setDaemon(true);
                return t;
            });

    private final FileObject fileObject;
    private final CodeArea codeArea;
    private final BreadcrumbsBar bar;
    private final EditorSettings settings;

    /** Monotonic id of the latest request; queries carrying an older id are stale. */
    private final AtomicLong requestSequence = new AtomicLong();

    /** The position shown instead of the caret ({@link #showFor}), {@code null} to follow the caret. */
    private TextPos revealed;

    /** Pending debounced kick-off, cancelled whenever a newer caret move arrives. */
    private volatile ScheduledFuture<?> pending;

    /**
     * Cached document snapshot, invalidated on model edits and lazily rebuilt on the next query,
     * so plain caret moves never re-extract the text of the whole document. Only touched on the
     * FX thread, like the fields below.
     */
    private String cachedText;

    /** Invalidates {@link #cachedText}; set by the model edit listener. */
    private boolean textDirty = true;

    /** Caret offset of the last completed query, to skip queries where nothing changed. */
    private int lastQueriedOffset = -1;

    /** Model edit listener installed while {@link #install()}'s subscription is alive. */
    private final StyledTextModel.Listener modelListener = change -> {
        if (change.isEdit()) {
            textDirty = true;
        }
    };

    /**
     * Creates the support for one editor.
     *
     * @param fileObject file shown in the editor
     * @param codeArea the editor's code area
     * @param bar the breadcrumbs bar to publish paths to
     * @param settings shared view settings, or {@code null} when none is registered
     */
    public BreadcrumbsSupport(FileObject fileObject, CodeArea codeArea, BreadcrumbsBar bar,
                              EditorSettings settings) {
        this.fileObject = Objects.requireNonNull(fileObject, "fileObject must not be null");
        this.codeArea = Objects.requireNonNull(codeArea, "codeArea must not be null");
        this.bar = Objects.requireNonNull(bar, "bar must not be null");
        this.settings = settings;
    }

    /**
     * Starts driving the bar: wires crumb navigation, subscribes to caret moves and to the
     * "show breadcrumbs" setting (querying right away when currently visible).
     *
     * @return subscription releasing every listener and cancelling any pending query
     */
    public Subscription install() {
        bar.setOnNavigate(this::navigateTo);
        StyledTextModel model = codeArea.getModel();
        if (model != null) {
            model.addListener(modelListener);
        }
        Subscription subscription = codeArea.caretPositionProperty()
                .subscribe((_, _) -> {
                    revealed = null;
                    schedule();
                });
        if (settings != null) {
            // The consumer variant fires immediately, scheduling the initial query when visible.
            // Forget the last queried offset so re-showing the bar always refreshes it.
            subscription = subscription.and(settings.showBreadcrumbs().subscribe(show -> {
                if (show) {
                    lastQueriedOffset = -1;
                    schedule();
                }
            }));
        } else {
            schedule();
        }
        return subscription.and(() -> {
            if (model != null) {
                model.removeListener(modelListener);
            }
            requestSequence.incrementAndGet();
            cancelPending();
        });
    }

    /**
     * Shows the path of {@code position} instead of the caret's - for a preview that reveals a place
     * without moving its caret - until the caret next moves.
     */
    public void showFor(TextPos position) {
        revealed = position;
        schedule();
    }

    /** Schedules a debounced breadcrumbs query for the current caret; no-op while hidden. */
    private void schedule() {
        if (isHidden()) {
            return;
        }
        requestSequence.incrementAndGet();
        cancelPending();
        pending = SCHEDULER.schedule(() -> Platform.runLater(this::query),
                BREADCRUMBS_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Snapshots the document and caret on the FX thread and runs the query on the first
     * provider that supports the file, publishing the resulting path unless outdated.
     * The document snapshot is only re-extracted after an edit; a caret move reuses the
     * cached one, and a query where neither the text nor the caret changed is skipped.
     */
    private void query() {
        if (isHidden()) {
            return;
        }
        CodeTextModel model = (CodeTextModel) codeArea.getModel();
        if (model == null) {
            return;
        }
        boolean textChanged = textDirty || cachedText == null;
        if (textChanged) {
            cachedText = SourceUtils.getFullText(model);
            textDirty = false;
        }
        TextPos position = revealed != null ? revealed : codeArea.getCaretPosition();
        int caretOffset = SourceUtils.toGlobalOffset(model, position);
        if (!textChanged && caretOffset == lastQueriedOffset) {
            return;
        }
        lastQueriedOffset = caretOffset;
        long requestId = requestSequence.incrementAndGet();
        BreadcrumbsContext context = new BreadcrumbsContext(fileObject, cachedText, caretOffset);
        BreadcrumbsProvider provider = Lookup.getDefault().lookupAll(BreadcrumbsProvider.class).stream()
                .filter(p -> p.supports(context))
                .findFirst()
                .orElse(null);
        if (provider == null) {
            bar.setPath(List.of());
            return;
        }
        provider.selectedElement(context, () -> requestId != requestSequence.get())
                .whenComplete((selected, error) -> Platform.runLater(() -> {
                    if (requestId != requestSequence.get()) {
                        return;
                    }
                    if (error != null) {
                        LOG.log(Level.WARNING, "Breadcrumbs query failed for " + fileObject.getNameExt(), error);
                        return;
                    }
                    bar.setPath(selected == null || selected.isEmpty()
                            ? List.of() : selected.get().path());
                }));
    }

    /** Moves the caret to the start of the given element and focuses the editor. */
    private void navigateTo(BreadcrumbElement element) {
        CodeTextModel model = (CodeTextModel) codeArea.getModel();
        if (model == null) {
            return;
        }
        codeArea.select(SourceUtils.toTextPos(model, element.startOffset()));
        codeArea.requestFocus();
    }

    private boolean isHidden() {
        return settings != null && !settings.showBreadcrumbs().get();
    }

    private void cancelPending() {
        ScheduledFuture<?> previous = pending;
        if (previous != null) {
            previous.cancel(false);
        }
    }
}

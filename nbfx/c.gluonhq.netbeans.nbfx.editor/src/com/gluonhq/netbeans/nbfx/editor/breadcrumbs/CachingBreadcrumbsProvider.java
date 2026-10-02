package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsContext;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsProvider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for the built-in breadcrumbs providers: matches files by extension,
 * runs the language-specific {@link #scan} on a worker thread, and caches the
 * resulting immutable breadcrumb roots keyed by file path and document text.
 *
 * <p>The cache holds the single most recent scan, which matches the one-editor-at-a-time
 * query pattern; a different file or an edited document simply misses and rescans.
 * A caret move reuses the cached roots and only re-runs the offset-based descent
 * ({@link BreadcrumbsElements#select}).</p>
 */
abstract class CachingBreadcrumbsProvider implements BreadcrumbsProvider {

    private record CachedScan(String filePath, String documentText, List<BreadcrumbElement> roots) {

        boolean matches(String path, String text) {
            return filePath.equals(path) && Objects.equals(documentText, text);
        }
    }

    private final Set<String> extensions;
    private volatile CachedScan cache;

    /**
     * @param extensions the (lower-case) file extensions this provider serves
     */
    CachingBreadcrumbsProvider(String... extensions) {
        this.extensions = Set.of(extensions);
    }

    @Override
    public final boolean supports(BreadcrumbsContext context) {
        return extensions.contains(context.fileObject().getExt().toLowerCase());
    }

    @Override
    public final CompletableFuture<Optional<BreadcrumbElement>> selectedElement(BreadcrumbsContext context,
                                                                                Cancellation cancellation) {
        return CompletableFuture.supplyAsync(() -> {
            if (cancellation.isCancelled()) {
                return Optional.empty();
            }
            List<BreadcrumbElement> roots = rootsFor(context, cancellation);
            if (roots == null || cancellation.isCancelled()) {
                return Optional.empty();
            }
            return BreadcrumbsElements.select(roots, context.caretOffset());
        });
    }

    /**
     * Parses {@code context.documentText()} into immutable breadcrumb roots.
     * Runs on a worker thread; must return {@code null} when the scan was
     * canceled or the source could not be parsed (the result is then not cached).
     *
     * @param context immutable breadcrumbs request snapshot
     * @param cancellation cancellation token for aborting stale work
     * @return breadcrumb roots, or {@code null}
     */
    abstract List<BreadcrumbElement> scan(BreadcrumbsContext context, Cancellation cancellation);

    /** Returns the (possibly cached) breadcrumb roots, or {@code null} when the scan failed. */
    private List<BreadcrumbElement> rootsFor(BreadcrumbsContext context, Cancellation cancellation) {
        String path = context.fileObject().getPath();
        CachedScan cached = cache;
        if (cached != null && cached.matches(path, context.documentText())) {
            return cached.roots();
        }
        List<BreadcrumbElement> roots = scan(context, cancellation);
        if (roots != null) {
            cache = new CachedScan(path, context.documentText(), roots);
        }
        return roots;
    }
}

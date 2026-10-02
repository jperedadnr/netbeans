package com.gluonhq.netbeans.nbfx.api.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.Cancellation;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provider for editor breadcrumbs queries.
 *
 * <p>Whenever the caret settles on a new position, the editor asks the first provider
 * that {@linkplain #supports(BreadcrumbsContext) supports} the file for the deepest
 * {@link BreadcrumbElement} enclosing the caret. The breadcrumbs bar then renders the
 * {@linkplain BreadcrumbElement#path() full path} of that element.</p>
 *
 * <p>Implementations are discovered through the global lookup and must be registered with
 * {@code @ServiceProvider(service = BreadcrumbsProvider.class)}. Queries run off the
 * JavaFX application thread; implementations must be thread-safe and should check the
 * cancellation token while scanning.</p>
 */
public interface BreadcrumbsProvider {

    /**
     * Checks whether this provider can serve breadcrumbs for the given context.
     *
     * @param context immutable breadcrumbs request snapshot
     * @return {@code true} if this provider should serve the query
     */
    boolean supports(BreadcrumbsContext context);

    /**
     * Computes the deepest breadcrumb element that encloses the caret position.
     *
     * @param context immutable breadcrumbs request snapshot
     * @param cancellation cancellation token for aborting stale work
     * @return async result with the selected element, or an empty optional when no
     * structural element encloses the caret (the bar then shows an empty path)
     */
    CompletableFuture<Optional<BreadcrumbElement>> selectedElement(BreadcrumbsContext context,
                                                                   Cancellation cancellation);
}

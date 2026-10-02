package com.gluonhq.netbeans.nbfx.api;

/**
 * Cancellation token passed to asynchronous providers (completion, breadcrumbs, …)
 * so stale queries can be abandoned.
 */
@FunctionalInterface
public interface Cancellation {

    /**
     * @return {@code true} if the query is stale or cancelled by the caller
     */
    boolean isCancelled();
}

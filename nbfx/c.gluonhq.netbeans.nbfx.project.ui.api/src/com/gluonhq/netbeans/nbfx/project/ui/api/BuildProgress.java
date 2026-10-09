package com.gluonhq.netbeans.nbfx.project.ui.api;

/**
 * The progress sink for an in-process build (the status bar), abstracted so build executors do not
 * depend on the progress module.
 *
 * @since 1.0
 */
public interface BuildProgress {

    /**
     * Shows cancellable progress while the build runs.
     *
     * @param name   the build's display name
     * @param cancel invoked when the user cancels the build
     */
    void start(String name, Runnable cancel);

    /** Hides the progress. */
    void finish();
}

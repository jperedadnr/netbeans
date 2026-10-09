package com.gluonhq.netbeans.nbfx.project.ui.api;

/**
 * A handle to a running in-process build, returned by {@link BuildActionProvider#start}.
 *
 * @since 1.0
 */
public interface BuildExecution {

    /** Requests cancellation of the running build. */
    void cancel();
}

package com.gluonhq.netbeans.nbfx.project.ui.api;

/**
 * The output sink for an in-process build: the Fx console, abstracted so build executors do not
 * depend on the output module.
 *
 * @since 1.0
 */
public interface BuildOutput {

    /** Clears the output. */
    void clear();

    /** Brings the output on screen. */
    void show();

    /** Appends {@code text}. */
    void append(String text);
}

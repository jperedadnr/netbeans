package com.gluonhq.netbeans.nbfx.project.ui.api;

/**
 * Standard build/run command identifiers, mirroring {@code org.netbeans.spi.project.ActionProvider}'s
 * command constants. A {@link BuildActionProvider} declares which of these it supports and returns a
 * command line for each.
 *
 * @since 1.0
 */
public final class BuildCommands {

    /** Incrementally builds the project. */
    public static final String BUILD = "build";
    /** Removes the build output. */
    public static final String CLEAN = "clean";
    /** Cleans then builds (a forced rebuild). */
    public static final String REBUILD = "rebuild";
    /** Runs the project. */
    public static final String RUN = "run";
    /** Runs the project's tests. */
    public static final String TEST = "test";
    /** Runs the project in the debugger. */
    public static final String DEBUG = "debug";
    /** Generates the project's javadoc. */
    public static final String JAVADOC = "javadoc";
    /** Compiles a single file. */
    public static final String COMPILE_SINGLE = "compile.single";
    /** Runs a single file. */
    public static final String RUN_SINGLE = "run.single";
    /** Tests a single file. */
    public static final String TEST_SINGLE = "test.single";

    private BuildCommands() {
    }
}

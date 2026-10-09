package com.gluonhq.netbeans.nbfx.api.actions;

/**
 * Stable identifiers for the built-in {@link Command}s, used to look them up from the
 * {@link ActionRegistry} and to wire UI controls to them.
 */
public final class ActionIds {

    /** Creates a new project. */
    public static final String NEW_PROJECT = "newProject";

    /** Creates a new file from a template. */
    public static final String NEW_FILE = "newFile";

    /** Opens an existing project. */
    public static final String OPEN_PROJECT = "openProject";

    /** Closes the currently selected project without closing the application. */
    public static final String CLOSE_PROJECT = "closeProject";

    /** Closes every open project without closing the application. */
    public static final String CLOSE_ALL_PROJECTS = "closeAllProjects";

    /** Selects the project after the selected one, wrapping around at the end of the list. */
    public static final String NEXT_PROJECT = "nextProject";

    /** Selects the project before the selected one, wrapping around at the start of the list. */
    public static final String PREVIOUS_PROJECT = "previousProject";

    /** Saves the active editor document. */
    public static final String SAVE = "save";

    /** Saves all modified editor documents. */
    public static final String SAVE_ALL = "saveAll";

    /** Saves the modified documents of the selected project. */
    public static final String SAVE_PROJECT = "saveProject";

    /** Undoes the last edit in the active editor document. */
    public static final String UNDO = "undo";

    /** Redoes the last undone edit in the active editor document. */
    public static final String REDO = "redo";

    /** Copies the current selection of the active editor document to the clipboard. */
    public static final String COPY = "copy";

    /** Cuts the current selection of the active editor document to the clipboard. */
    public static final String CUT = "cut";

    /** Pastes the clipboard content into the active editor document. */
    public static final String PASTE = "paste";

    /** Copies the files selected in the navigator to the clipboard. */
    public static final String FILE_COPY = "file.copy";

    /** Cuts the files selected in the navigator to the clipboard. */
    public static final String FILE_CUT = "file.cut";

    /** Pastes the clipboard files into the folder selected in the navigator. */
    public static final String FILE_PASTE = "file.paste";

    /** Undoes the last file operation. */
    public static final String FILE_UNDO = "file.undo";

    /** Redoes the last undone file operation. */
    public static final String FILE_REDO = "file.redo";

    /** Shows/selects the Projects navigator tab. */
    public static final String SELECT_PROJECTS = "selectProjects";

    /** Shows/selects the Files navigator tab. */
    public static final String SELECT_FILES = "selectFiles";

    /** Selects and focuses the main editor pane. */
    public static final String SELECT_EDITOR = "selectEditor";

    /** Restores the default window layout. */
    public static final String RESET_WINDOWS = "resetWindows";

    /** Closes the selected editor document. */
    public static final String CLOSE_DOCUMENT = "closeDocument";

    /** Closes all open editor documents. */
    public static final String CLOSE_ALL_DOCUMENTS = "closeAllDocuments";

    /** Closes all open editor documents except the selected one. */
    public static final String CLOSE_OTHER_DOCUMENTS = "closeOtherDocuments";

    /** Selects the file of the active editor in the Projects view (editor context menu). */
    public static final String SELECT_IN_PROJECTS = "selectInProjects";

    /** Shows the Find bar of the active editor (Edit menu). */
    public static final String FIND = "find";
    /** Moves to the next match of the active editor's search (Edit menu). */
    public static final String FIND_NEXT = "findNext";
    /** Moves to the previous match of the active editor's search (Edit menu). */
    public static final String FIND_PREVIOUS = "findPrevious";
    /** Searches for the active editor's selection, or the identifier at its caret (Edit menu). */
    public static final String FIND_SELECTION = "findSelection";
    /** Edit &#9656; Replace...: shows the editor's Find and Replace bars. */
    public static final String REPLACE = "replace";

    /** Finds the usages of the element at the caret of the active editor (Edit menu, editor context menu). */
    public static final String FIND_USAGES = "findUsages";
    /** Shows and selects the Usages view (Window menu). */
    public static final String SELECT_USAGES = "selectUsages";

    /** Edit &#9656; Find in Projects...: opens the project-wide search dialog. */
    public static final String FIND_IN_PROJECTS = "findInProjects";
    /** Edit &#9656; Replace in Projects...: opens the project-wide search dialog in replace mode. */
    public static final String REPLACE_IN_PROJECTS = "replaceInProjects";
    /** Shows and selects the Search Results view (Window menu). */
    public static final String SELECT_SEARCH_RESULTS = "selectSearchResults";

    /** Shows and selects the Output view (Window menu). */
    public static final String SELECT_OUTPUT = "selectOutput";

    /** Shows and selects the Plugins view (Window menu). */
    public static final String SELECT_PLUGINS = "selectPlugins";

    /** Opens the Options dialog (Window menu). */
    public static final String SELECT_OPTIONS = "selectOptions";

    /** Shows and selects the Git view (Window menu). */
    public static final String SELECT_GIT = "selectGit";

    /** Debugs the active file. */
    public static final String DEBUG_FILE = "debugFile";

    /** Toggles a breakpoint on the active editor's current line. */
    public static final String TOGGLE_BREAKPOINT = "toggleBreakpoint";

    /** Resumes the running debug session. */
    public static final String DEBUG_CONTINUE = "debugContinue";

    /** Steps over in the running debug session. */
    public static final String DEBUG_STEP_OVER = "debugStepOver";

    /** Steps into in the running debug session. */
    public static final String DEBUG_STEP_INTO = "debugStepInto";

    /** Steps out in the running debug session. */
    public static final String DEBUG_STEP_OUT = "debugStepOut";

    /** Stops the running debug session. */
    public static final String DEBUG_STOP = "debugStop";
    /** Find... on a folder of the Projects view: opens the search dialog scoped to the selected folders. */
    public static final String FILE_FIND = "file.find";

    /** Builds the selected project with its build tool. */
    public static final String BUILD = "build";

    /** Cleans and then builds the selected project. */
    public static final String CLEAN_BUILD = "cleanBuild";

    /** Cleans the selected project's build output. */
    public static final String CLEAN = "clean";

    /** Runs the selected project's tests. */
    public static final String TEST = "test";

    /** Runs the selected project. */
    public static final String RUN = "run";

    /** Generates the selected project's javadoc. */
    public static final String JAVADOC = "javadoc";

    private ActionIds() {
    }
}

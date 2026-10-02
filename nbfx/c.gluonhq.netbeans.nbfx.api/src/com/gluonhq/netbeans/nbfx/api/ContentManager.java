package com.gluonhq.netbeans.nbfx.api;

import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.EditorService;
import com.gluonhq.netbeans.nbfx.api.file.FileIconProvider;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;

import java.util.List;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.openide.filesystems.FileObject;

public interface ContentManager {

    /**
     * Opens {@code file} in an editor tab: if a tab for it already exists (in any pane), selects it;
     * otherwise creates a new document (via {@link EditorService}) and adds a tab to the main pane.
     *
     * @param file    the file to open
     * @param graphic optional tab graphic (icon); when {@code null} the icon registered for the file
     *                by a {@link FileIconProvider} is used, if any
     */
    void openFile(FileObject file, Node graphic);

    /**
     * Opens {@code file} as {@link #openFile(FileObject, Node)} does (with the registered icon),
     * then {@linkplain EditorDocument#selectRange selects} the range {@code [start, end)} of its
     * content in the shown document and focuses it - the way a usage or a search result is shown.
     *
     * @param file  the file to open
     * @param start the offset of the first character to select
     * @param end   the offset after the last character to select
     */
    void openFile(FileObject file, int start, int end);

    /**
     * Closes every open editor tab across all windows (main and detached), unregistering their
     * documents.
     */
    void closeAll();

    /**
     * Closes only the editor tabs of the files belonging to the project rooted at {@code projectPath},
     * across all windows (main and detached), unregistering their documents.
     * <p>
     * Documents keep the project they were opened in ({@link EditorDocument#getProjectPath()}), so
     * this works whether the project is still open or has already been closed.
     *
     * @param projectPath the {@link OpenProject#getPath() path} of the project whose files are
     *                    closed
     */
    void closeFiles(String projectPath);

    /** The documents currently open in the main pane, in tab order. */
    List<EditorDocument> getMainPaneDocuments();

    /**
     * The documents of the project rooted at {@code projectPath} that are open in any pane (main or
     * detached), in pane and tab order.
     *
     * @param projectPath the {@link OpenProject#getPath() path} of a project; {@code null} selects
     *                    the documents that belong to no open project
     * @return an immutable snapshot of the matching documents
     */
    List<EditorDocument> documentsOf(String projectPath);

    /**
     * The main pane's active (selected) document as an observable value, for driving the main
     * window's toolbar/menu enablement from its own selected editor (independent of focus).
     */
    ObservableValue<EditorDocument> mainPaneActiveDocument();

    /** The file of the selected tab in the main pane, or {@code null} if none. */
    FileObject getActiveFile();

    /** Selects the main-pane tab showing {@code file}, if present. */
    void selectFile(FileObject file);

    /** Requests focus on the main pane's selected editor (so its caret blinks), if any. */
    void focusActiveEditor();

    /** Closes every tab showing {@code file} (its document and any clones), in any pane, if present. */
    void closeFile(FileObject file);

    /**
     * Closes the tab of {@code document} in any pane (main or detached), if present. Unlike
     * {@link #closeFile}, which closes every tab of a file, this only closes the one tab of the given
     * document, leaving any {@linkplain EditorDocument#cloneDocument() clones} of it open.
     */
    void closeDocument(EditorDocument document);

    /**
     * Opens a {@linkplain EditorDocument#cloneDocument() clone} of {@code document} in a new tab next
     * to it, in the same pane, and selects it. Does nothing if the document cannot be cloned.
     *
     * @return the clone, or {@code null} if none was opened
     */
    EditorDocument cloneDocument(EditorDocument document);

    /**
     * Deletes {@code file} from disk, closing its open editor tab (in any pane) if present.
     *
     * @throws java.io.IOException if the file cannot be deleted
     */
    void deleteFile(FileObject file) throws java.io.IOException;

    /**
     * Deletes {@code folder} and all of its contents recursively, closing the editor tabs of any
     * open files being removed.
     *
     * @throws java.io.IOException if the folder cannot be deleted
     */
    void deleteFolder(FileObject folder) throws java.io.IOException;

    /** The document showing {@code file} in any pane (main or detached), or {@code null} if none. */
    EditorDocument documentForFile(FileObject file);

}

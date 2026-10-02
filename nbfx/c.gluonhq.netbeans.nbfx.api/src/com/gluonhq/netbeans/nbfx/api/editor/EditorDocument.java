package com.gluonhq.netbeans.nbfx.api.editor;

import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;

import java.io.IOException;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * A savable, dirty-aware view of a single open editor.
 * <p>
 * An {@code EditorDocument} wraps the editor UI ({@link #getNode()}) together with the
 * {@link FileObject} it edits, tracks whether it has unsaved changes
 * ({@link #modifiedProperty()}), and can persist those changes back to the file
 * ({@link #save()}).
 */
public interface EditorDocument {

    /**
     * Returns the file this document edits.
     *
     * @return the backing {@link FileObject}, never {@code null}
     */
    FileObject getFileObject();

    /**
     * The {@link OpenProject#getPath() path} of the open project this document's file belongs to,
     * or {@code null} when it belongs to no open project.
     *
     * @return the owning project's path, or {@code null}
     */
    default String getProjectPath() {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject project = registry == null ? null : registry.ownerOf(getFileObject());
        return project == null ? null : project.getPath();
    }

    /**
     * Returns the root node of the editor UI, suitable for adding to a scene graph.
     *
     * @return the editor's root node
     */
    Node getNode();

    /**
     * Returns a short, human-readable title for this document (typically the file name).
     *
     * @return the document title
     */
    String getTitle();

    /**
     * Indicates whether the document has unsaved changes.
     *
     * @return {@code true} if there are edits that have not yet been persisted
     */
    boolean isModified();

    /**
     * The observable modified (dirty) state of this document. UI can bind to this property
     * to reflect the dirty state (for example, marking a tab).
     *
     * @return the read-only modified property
     */
    ReadOnlyBooleanProperty modifiedProperty();

    /**
     * Persists the current editor content back to the {@link FileObject} and clears the
     * modified flag.
     * <p>
     * Implementations must never fail silently: any write conflict or I/O failure is
     * reported by throwing an {@link IOException}.
     *
     * @throws IOException if the file was modified externally since it was opened, or if
     *                     writing the content fails
     */
    void save() throws IOException;

    /**
     * Undoes the most recent edit, if any..
     */
    void undo();

    /**
     * Redoes the most recently undone edit, if any.
     */
    void redo();

    /**
     * Copies the current selection to the system clipboard.
     */
    void copy();

    /**
     * Cuts the current selection to the system clipboard.
     */
    void cut();

    /**
     * Pastes the system clipboard content at the caret.
     */
    void paste();

    /**
     * Observable state indicating whether an {@link #undo()} is currently possible. UI can bind
     * to this property to enable or disable an Undo action.
     *
     * @return the read-only can-undo property
     */
    ReadOnlyBooleanProperty canUndoProperty();

    /**
     * Observable state indicating whether a {@link #redo()} is currently possible. UI can bind
     * to this property to enable or disable a Redo action.
     *
     * @return the read-only can-redo property
     */
    ReadOnlyBooleanProperty canRedoProperty();

    /**
     * Observable state indicating whether the document currently has a non-empty selection
     * (so that Copy / Cut apply). UI can bind to this property.
     *
     * @return the read-only has-selection property
     */
    ReadOnlyBooleanProperty hasSelectionProperty();

    /**
     * The selected text with {@code '\n'} line breaks, or the empty string when nothing is selected
     * (Find in Projects seeds its Containing Text with it). The default implementation returns the
     * empty string.
     */
    default String getSelectedText() {
        return "";
    }

    /**
     * Observable state indicating whether the document currently accepts edits (so that Paste
     * applies). UI can bind to this property.
     *
     * @return the read-only editable property
     */
    ReadOnlyBooleanProperty editableProperty();

    /**
     * The live caret position and selection size of this document, so the UI (typically the status
     * bar) can follow the caret as it moves. The value is {@code null} when there is no caret.
     *
     * @return the read-only caret-info property
     */
    ObservableValue<CaretInfo> caretInfoProperty();

    /**
     * The observable line separator of this document's file ({@code "\n"}, {@code "\r"} or
     * {@code "\r\n"}), so the UI (typically the status bar) can name it and follow it when the
     * user {@linkplain #setLineSeparator(String) changes} it. The value is {@code null} when
     * unknown.
     *
     * @return the read-only line-separator property
     */
    ObservableValue<String> lineSeparatorProperty();

    /**
     * Changes the line separator this document uses when its content is read or
     * {@linkplain #save() saved}, marking the document as modified when it differs from the saved
     * one.
     *
     * @param separator the new line separator: {@code "\n"}, {@code "\r"} or {@code "\r\n"}
     */
    void setLineSeparator(String separator);

    /**
     * The paragraph (zero-based line) index of the caret, used to persist and restore the
     * editing position.
     *
     * @return the caret's paragraph index
     */
    default int getCaretParagraph() {
        return 0;
    }

    /**
     * The column (character offset within the paragraph) of the caret, used to persist and
     * restore the editing position.
     *
     * @return the caret's column offset
     */
    default int getCaretColumn() {
        return 0;
    }

    /**
     * The paragraph (zero-based line) index currently shown at the top of the viewport, used to
     * persist and restore the scroll position.
     *
     * @return the first visible paragraph index
     */
    default int getTopParagraph() {
        return 0;
    }

    /**
     * Restores the editor's scroll and caret state: scrolls so {@code topParagraph} is at the top of
     * the viewport and places the caret at {@code caretParagraph}/{@code caretColumn}.
     *
     * @param topParagraph   the paragraph to show at the top of the viewport
     * @param caretParagraph the zero-based paragraph (line) index of the caret
     * @param caretColumn    the character offset within the caret's paragraph
     */
    default void restoreView(int topParagraph, int caretParagraph, int caretColumn) {
    }

    /**
     * The caret position as a character offset from the start of the content, counting one
     * character per line break whatever the file's line separator - the offsets the NetBeans
     * source model ({@code TreePathHandle}, {@code PositionBounds}) works with.
     *
     * @return the caret offset, or {@code 0} when there is no caret
     */
    default int getCaretOffset() {
        return 0;
    }

    /**
     * Selects the range {@code [start, end)} of the content, in the offsets of
     * {@link #getCaretOffset()}, placing the caret at {@code end} and scrolling it into view. Used
     * to show a usage or a search result. Out-of-range offsets are clamped to the content; the
     * default implementation does nothing.
     *
     * @param start the offset of the first selected character
     * @param end   the offset after the last selected character (equal to {@code start} to just
     *              move the caret)
     */
    default void selectRange(int start, int end) {
    }

    /**
     * Replaces the text in {@code [start, end)} (offsets in the document's {@code '\n'}-counted
     * text) with {@code text} as one undoable edit, marking the document modified: how Replace in
     * Projects edits a file that is open in an editor, so its buffer and its Undo history see the
     * change. The default implementation does nothing and returns {@code false}.
     *
     * @return whether the edit was applied ({@code false} for a read-only document)
     */
    default boolean replaceRange(int start, int end, String text) {
        return false;
    }

    /**
     * Brings the range {@code [start, end)} into view - in the middle of the viewport when possible -
     * and marks its line, without moving the caret or the selection: what a preview does when a
     * result is picked. The mark stays until the next call or the document is disposed. The default
     * implementation selects the range instead.
     */
    default void revealRange(int start, int end) {
        selectRange(start, end);
    }

    /**
     * Requests keyboard focus for the editor so the caret is shown. The default implementation does
     * nothing.
     */
    default void requestFocus() {
    }

    /**
     * Shows the editor's Find bar (Edit ▸ Find...), seeded with the selection when it lies within
     * one line, and focuses its field; a bar already shown is re-focused. The default
     * implementation does nothing.
     */
    default void showFind() {
    }

    /**
     * Shows the Find bar with the Replace row under it (Edit ▸ Replace..., Shortcut+R), seeded
     * and focused like {@link #showFind()}; a read-only document just shows Find. The default
     * implementation does nothing.
     */
    default void showReplace() {
    }

    /**
     * Moves the selection to the next match of the current search query (Edit ▸ Find Next, F3),
     * opening the Find bar when there is no query yet. The default implementation does nothing.
     */
    default void findNext() {
    }

    /**
     * Moves the selection to the previous match of the current search query (Edit ▸ Find Previous,
     * Shift+F3), opening the Find bar when there is no query yet. The default implementation does
     * nothing.
     */
    default void findPrevious() {
    }

    /**
     * Searches for the selection - or the identifier at the caret - and moves to its next
     * occurrence, without opening the Find bar (Edit ▸ Find Selection). The default implementation
     * does nothing.
     */
    default void findSelection() {
    }

    /**
     * Releases resources held by this document (for example file listeners) when its editor tab is
     * closed. The document is not usable afterwards. The default implementation does nothing.
     */
    default void dispose() {
    }

    /**
     * Creates another document showing the same content as this one - the NetBeans <em>Clone
     * Document</em>: both share the text, undo history, dirty state and saving, while each keeps its
     * own caret, selection and scroll position. Neither is the <em>original</em>: closing one leaves
     * the other fully functional, and the shared content is only released with the last of them.
     * The default implementation returns {@code null}: the document cannot be cloned.
     *
     * @return the clone, or {@code null} if this document does not support cloning
     */
    default EditorDocument cloneDocument() {
        return null;
    }

    /**
     * Whether {@code other} shows the same content as this document, i.e. is this document or one
     * of its {@link #cloneDocument() clones}. The default implementation only recognises the
     * document itself.
     */
    default boolean sharesContentWith(EditorDocument other) {
        return this == other;
    }

}

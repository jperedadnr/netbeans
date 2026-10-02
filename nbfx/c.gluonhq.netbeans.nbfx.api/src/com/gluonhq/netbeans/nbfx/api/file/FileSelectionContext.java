package com.gluonhq.netbeans.nbfx.api.file;

import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;

import java.util.List;

import javafx.beans.value.ObservableValue;

import org.openide.filesystems.FileObject;

/**
 * The shared, observable state of the navigator file selection: which files are selected in the
 * navigator views and whether one of those views owns the keyboard focus.
 * <p>
 * Like {@link EditorContext} for the editor commands, this is the context the file-scoped
 * commands (file Cut / Copy / Paste / Undo / Redo) are driven by. It is updated by the
 * application shell (which owns the navigator views) and observed by the file action providers.
 * The context is resolved through the global {@link org.openide.util.Lookup}.
 */
public interface FileSelectionContext {

    /**
     * The files currently selected in the navigator, in selection order. Never {@code null};
     * an empty list when nothing is selected.
     *
     * @return the observable navigator selection
     */
    ObservableValue<List<FileObject>> selectedFiles();

    /**
     * Publishes a new navigator selection.
     *
     * @param files the selected files, or an empty list when nothing is selected
     */
    void setSelectedFiles(List<FileObject> files);

    /**
     * Whether the keyboard focus is currently inside a navigator view. Drives the dispatch of
     * the shared Cut/Copy/Paste/Undo/Redo ids between their file and editor variants.
     *
     * @return the observable navigator-focused state
     */
    ObservableValue<Boolean> navigatorFocused();

    /**
     * Publishes whether the keyboard focus is inside a navigator view.
     *
     * @param focused {@code true} while a navigator view owns the focus
     */
    void setNavigatorFocused(boolean focused);
}

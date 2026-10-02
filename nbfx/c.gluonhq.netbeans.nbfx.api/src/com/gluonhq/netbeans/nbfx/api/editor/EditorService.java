package com.gluonhq.netbeans.nbfx.api.editor;

import com.gluonhq.netbeans.nbfx.api.ContentManager;
import org.openide.filesystems.FileObject;

/**
 * Factory for editor documents. The resulting {@link EditorDocument} is handed to the
 * {@link ContentManager}.
 */
public interface EditorService {

    /** Creates a new editor document for {@code file}. */
    EditorDocument createDocument(FileObject file);

    /**
     * Creates a read-only <em>preview</em> of {@code file}: an editor without code completion,
     * diagnostics or file watching, meant to show a file (e.g. the usage a tree node points to)
     * rather than to edit it. When the file is open in an editor, the preview shows that editor's
     * live content, as a {@linkplain EditorDocument#cloneDocument() clone} of it would. The default
     * implementation returns an ordinary document.
     */
    default EditorDocument createPreviewDocument(FileObject file) {
        return createDocument(file);
    }

}

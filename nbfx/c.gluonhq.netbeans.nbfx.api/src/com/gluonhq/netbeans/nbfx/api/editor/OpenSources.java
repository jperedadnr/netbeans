package com.gluonhq.netbeans.nbfx.api.editor;

import java.util.Set;
import org.openide.filesystems.FileObject;

/**
 * The current text of the files open in editors, for source analyses that run outside the editor
 * (such as Find Usages) and must see unsaved edits, the way they would in NetBeans. Registered in
 * the global {@link org.openide.util.Lookup} by the editor module.
 * <p>
 * An analysis substitutes {@link #textOf} for the content on disk of every
 * {@link #modifiedFiles modified file} it parses (for instance through in-memory
 * {@code FileObject}s handed to {@code JavaSource.create}). The methods are safe to call from any
 * thread and return snapshots: the text as of the last edit, with one {@code '\n'} per line break
 * whatever the file's line separator.
 */
public interface OpenSources {

    /** The open files whose editor content differs from the file on disk. */
    Set<FileObject> modifiedFiles();

    /**
     * The editor content of {@code file}, or {@code null} when the file is not open in any editor.
     */
    String textOf(FileObject file);
}

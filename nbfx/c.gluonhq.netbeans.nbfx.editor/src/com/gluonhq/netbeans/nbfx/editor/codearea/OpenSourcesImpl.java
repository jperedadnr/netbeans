package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import java.util.Set;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/** Publishes the open {@link EditorBuffer}s' text; see {@link OpenSources}. */
@ServiceProvider(service = OpenSources.class)
public final class OpenSourcesImpl implements OpenSources {

    @Override
    public Set<FileObject> modifiedFiles() {
        return EditorBuffer.modifiedFiles();
    }

    @Override
    public String textOf(FileObject file) {
        EditorBuffer buffer = file == null ? null : EditorBuffer.openBuffer(file);
        return buffer == null ? null : buffer.snapshot();
    }
}

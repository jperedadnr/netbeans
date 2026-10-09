package com.gluonhq.netbeans.nbfx.properties;

import java.util.List;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Contributes a read-only "General" property set for a {@link FileObject}: name, path, kind and size.
 */
@ServiceProvider(service = FxPropertiesProvider.class)
public final class FilePropertiesProvider implements FxPropertiesProvider {

    @Override
    public List<FxPropertySet> getPropertySets(Object context) {
        if (!(context instanceof FileObject file)) {
            return List.of();
        }
        List<FxProperty> properties = List.of(
                readOnly("name", message("FilePropertiesProvider.name"), file.getNameExt()),
                readOnly("path", message("FilePropertiesProvider.path"), file.getPath()),
                readOnly("kind", message("FilePropertiesProvider.kind"),
                        message(file.isFolder() ? "FilePropertiesProvider.folder" : "FilePropertiesProvider.file")),
                readOnly("size", message("FilePropertiesProvider.size"), size(file)));
        return List.of(new SimpleFxPropertySet("file", message("FilePropertiesProvider.general"), properties));
    }

    private static String size(FileObject file) {
        if (file.isFolder()) {
            return "-";
        }
        long size = file.getSize();
        return size < 0 ? "?" : String.valueOf(size);
    }

    private static FxProperty readOnly(String name, String displayName, Object value) {
        return new SimpleFxProperty(name, displayName, null, String.class, value, false, false, value);
    }

    private static String message(String key) {
        return NbBundle.getMessage(FilePropertiesProvider.class, key);
    }
}

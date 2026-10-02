package com.gluonhq.netbeans.nbfx.api.actions;

import java.util.List;
import javafx.scene.control.MenuItem;
import org.openide.filesystems.FileObject;

/**
 * Adds items to the context menu of a file or folder node in the Projects and Files views.
 * Implementations are registered in the default Lookup ({@code @ServiceProvider}) by the module
 * that owns the feature (e.g. Find Usages, Find in Projects), so the navigator does not depend on
 * it. The views ask every contributor each time a menu opens, on the JavaFX thread, and place the
 * items - in the order returned, contributors in Lookup order - in their own section after
 * "Delete" (file nodes) or before the terminal / file-manager items (folder nodes).
 */
public interface FileContextMenuContributor {

    /**
     * The items for {@code file} (never a folder), or an empty list when the contributor has
     * nothing to offer for it. A fresh list of new items is expected on every call.
     */
    List<MenuItem> itemsFor(FileObject file);

    /**
     * The items for a selection of {@code folders} - a package, a folder, a project root, or
     * several of them selected together -, or an empty list when the contributor has nothing to
     * offer. Nothing by default. A fresh list of new items is expected on every call.
     */
    default List<MenuItem> itemsForFolders(List<FileObject> folders) {
        return List.of();
    }
}

package com.gluonhq.netbeans.nbfx.api.project;

import org.openide.filesystems.FileObject;

/**
 * Opens a project into the running application: loads its tree into the navigator views and adds it
 * to the {@link ProjectRegistry}. The navigator context menu ("Open Project" on a subproject) and the
 * double-click handler use this, so opening a project always goes through the same full flow as
 * File &gt; Open Project.
 *
 * @since 1.0
 */
public interface ProjectOpener {

    /** Opens the project rooted at {@code root}. A no-op when the application is not running. */
    void open(FileObject root);
}

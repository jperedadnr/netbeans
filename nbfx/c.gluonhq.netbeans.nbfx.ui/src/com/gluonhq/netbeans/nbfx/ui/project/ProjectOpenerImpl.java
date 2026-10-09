package com.gluonhq.netbeans.nbfx.ui.project;

import com.gluonhq.netbeans.nbfx.api.project.ProjectOpener;
import com.gluonhq.netbeans.nbfx.ui.JavaFXLaunchApp;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 * The {@link ProjectOpener} implementation: delegates to the running {@link JavaFXLaunchApp}, which
 * owns the navigator providers and the project registry.
 *
 * @since 1.0
 */
@ServiceProvider(service = ProjectOpener.class)
public final class ProjectOpenerImpl implements ProjectOpener {

    @Override
    public void open(FileObject root) {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        if (app != null) {
            app.openProject(root);
        }
    }
}

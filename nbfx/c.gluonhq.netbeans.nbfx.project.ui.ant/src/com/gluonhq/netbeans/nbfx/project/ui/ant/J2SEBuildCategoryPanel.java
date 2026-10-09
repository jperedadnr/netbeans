package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import javafx.scene.Node;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "Build" folder, holding the Compiling, Packaging and Documenting sub-categories. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SEBuildCategoryPanel extends J2SECustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "build";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SEBuildCategoryPanel.class, "Build.displayName");
    }

    @Override
    public int position() {
        return 300;
    }

    @Override
    public Node createPanel(Project project) {
        return null;
    }
}

package com.gluonhq.netbeans.nbfx.project.ui.ant;
import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;

import javafx.scene.Node;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Ant / NetBeans-module "Build" folder, holding the {@code Compiling} and {@code Packaging}
 * sub-categories.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class AntBuildCategoryPanel extends AntModuleCustomizerPanel {

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
        return NbBundle.getMessage(AntBuildCategoryPanel.class, "Build.displayName");
    }

    @Override
    public int position() {
        return 500;
    }

    @Override
    public Node createPanel(Project project) {
        return null;
    }
}

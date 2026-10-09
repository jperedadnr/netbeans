package com.gluonhq.netbeans.nbfx.project.ui.maven;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import javafx.scene.Node;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** Maven "Build" folder, holding the Compile sub-category. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class MavenBuildCategoryPanel implements FxProjectCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "maven";
    }

    @Override
    public String id() {
        return "build";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(MavenBuildCategoryPanel.class, "Build.displayName");
    }

    @Override
    public int position() {
        return 277;
    }

    @Override
    public Node createPanel(Project project) {
        return null;
    }
}

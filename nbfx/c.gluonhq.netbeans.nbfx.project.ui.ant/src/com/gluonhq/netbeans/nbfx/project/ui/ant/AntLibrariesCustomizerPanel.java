package com.gluonhq.netbeans.nbfx.project.ui.ant;
import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;

import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectLibrary;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Ant / NetBeans-module "Libraries" project-properties category: the project's module
 * dependencies. Read-only for now.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class AntLibrariesCustomizerPanel extends AntModuleCustomizerPanel {

    private final AntProjectKindProvider kind = new AntProjectKindProvider();

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "libraries";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(AntLibrariesCustomizerPanel.class, "Libraries.displayName");
    }

    @Override
    public int position() {
        return 200;
    }

    @Override
    public Node createPanel(Project project) {
        VBox box = new VBox(4);
        List<ProjectLibrary> libraries = kind.libraries(project);
        if (libraries.isEmpty()) {
            box.getChildren().add(new Label(NbBundle.getMessage(AntLibrariesCustomizerPanel.class, "Libraries.none")));
        } else {
            for (ProjectLibrary library : libraries) {
                String text = library.detail() == null
                        ? library.name()
                        : library.name() + " (" + library.detail() + ")";
                box.getChildren().add(new Label(text));
            }
        }
        return box;
    }
}

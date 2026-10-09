package com.gluonhq.netbeans.nbfx.project.ui.ant;
import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Ant / NetBeans-module "API Versioning" category: the public packages, stored in
 * {@code nbproject/project.xml}.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class AntVersioningCustomizerPanel extends AntModuleCustomizerPanel {

    private TextArea publicPackages;
    private String initial;

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "versioning";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(AntVersioningCustomizerPanel.class, "Versioning.displayName");
    }

    @Override
    public int position() {
        return 400;
    }

    @Override
    public Node createPanel(Project project) {
        List<String> packages = AntProjectFiles.publicPackages(project.getProjectDirectory());
        initial = String.join("\n", packages);
        publicPackages = new TextArea(initial);
        publicPackages.setPrefRowCount(10);
        publicPackages.setPrefColumnCount(40);
        VBox box = new VBox(6, new Label(NbBundle.getMessage(AntVersioningCustomizerPanel.class,
                "Versioning.publicPackages")), publicPackages);
        return box;
    }

    @Override
    public boolean isChanged() {
        return !initial.equals(publicPackages.getText());
    }

    @Override
    public void apply(Project project) {
        List<String> packages = new ArrayList<>();
        for (String line : publicPackages.getText().split("\n")) {
            String pkg = line.trim();
            if (!pkg.isEmpty()) {
                packages.add(pkg);
            }
        }
        AntProjectFiles.setPublicPackages(project.getProjectDirectory(), packages);
    }
}

package com.gluonhq.netbeans.nbfx.project.ui.ant;
import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Ant / NetBeans-module "Build / Packaging" category: the module's specification and
 * implementation versions, stored in {@code manifest.mf}.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class AntPackagingCustomizerPanel extends AntModuleCustomizerPanel {

    private TextField specificationVersion;
    private String initialSpecification;

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "build.packaging";
    }

    @Override
    public String parentId() {
        return "build";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(AntPackagingCustomizerPanel.class, "Packaging.displayName");
    }

    @Override
    public int position() {
        return 200;
    }

    @Override
    public Node createPanel(Project project) {
        FileObject dir = project.getProjectDirectory();
        initialSpecification = AntProjectFiles.manifestEntry(dir, "OpenIDE-Module-Specification-Version");
        specificationVersion = new TextField(initialSpecification == null ? "" : initialSpecification);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        grid.addRow(0, new Label(NbBundle.getMessage(AntPackagingCustomizerPanel.class,
                "Packaging.specificationVersion")), specificationVersion);
        return grid;
    }

    @Override
    public boolean isChanged() {
        return !(initialSpecification == null ? "" : initialSpecification).equals(specificationVersion.getText().trim());
    }

    @Override
    public void apply(Project project) {
        if (isChanged()) {
            AntProjectFiles.setManifestEntry(project.getProjectDirectory(),
                    "OpenIDE-Module-Specification-Version", specificationVersion.getText().trim());
        }
    }
}

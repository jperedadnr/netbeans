package com.gluonhq.netbeans.nbfx.project.customizer;

import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKindProvider;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKinds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The type-agnostic "General" category: the project's name, type and location. Read-only, so it
 * demonstrates the dialog and gives every project at least one category.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class GeneralCustomizerPanel implements FxProjectCustomizerPanel {

    @Override
    public String id() {
        return "general";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(GeneralCustomizerPanel.class, "General.displayName");
    }

    @Override
    public int position() {
        return 0;
    }

    @Override
    public Node createPanel(Project project) {
        ProjectKindProvider kind = ProjectKinds.providerOf(project);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        int row = 0;
        addRow(grid, row++, "General.name", ProjectKinds.getProjectName(project, kind));
        addRow(grid, row++, "General.type", kind.id());
        addRow(grid, row, "General.location", project.getProjectDirectory().getPath());
        return grid;
    }

    private static void addRow(GridPane grid, int row, String key, String value) {
        Label name = new Label(NbBundle.getMessage(GeneralCustomizerPanel.class, key) + ":");
        name.setStyle("-fx-font-weight: bold;");
        grid.add(name, 0, row);
        grid.add(new Label(value == null ? "" : value), 1, row);
    }
}

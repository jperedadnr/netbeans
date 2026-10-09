package com.gluonhq.netbeans.nbfx.project.ui.gradle;

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
 * The Gradle "Project Information" category: the project's group, version and description, stored in
 * {@code gradle.properties}.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class GradleProjectInformationCustomizerPanel implements FxProjectCustomizerPanel {

    private TextField group;
    private TextField version;
    private TextField description;
    private String initialGroup;
    private String initialVersion;
    private String initialDescription;

    @Override
    public String projectTypeId() {
        return "gradle";
    }

    @Override
    public String id() {
        return "info";
    }

    @Override
    public String displayName() {
        return message("Info.displayName");
    }

    @Override
    public int position() {
        return 200;
    }

    @Override
    public Node createPanel(Project project) {
        FileObject dir = project.getProjectDirectory();
        initialGroup = GradleProjectFiles.property(dir, "group");
        initialVersion = GradleProjectFiles.property(dir, "version");
        initialDescription = GradleProjectFiles.property(dir, "description");
        group = new TextField(orEmpty(initialGroup));
        version = new TextField(orEmpty(initialVersion));
        description = new TextField(orEmpty(initialDescription));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        grid.addRow(0, label("Info.group"), group);
        grid.addRow(1, label("Info.version"), version);
        grid.addRow(2, label("Info.description"), description);
        return grid;
    }

    @Override
    public boolean isChanged() {
        return changed(initialGroup, group) || changed(initialVersion, version)
                || changed(initialDescription, description);
    }

    @Override
    public void apply(Project project) {
        FileObject dir = project.getProjectDirectory();
        setIfChanged(dir, "group", initialGroup, group);
        setIfChanged(dir, "version", initialVersion, version);
        setIfChanged(dir, "description", initialDescription, description);
    }

    private static void setIfChanged(FileObject dir, String key, String initial, TextField field) {
        if (changed(initial, field)) {
            GradleProjectFiles.setProperty(dir, key, field.getText().trim());
        }
    }

    private static boolean changed(String initial, TextField field) {
        return !orEmpty(initial).equals(field.getText().trim());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Label label(String key) {
        return new Label(message(key));
    }

    private static String message(String key) {
        return NbBundle.getMessage(GradleProjectInformationCustomizerPanel.class, key);
    }
}

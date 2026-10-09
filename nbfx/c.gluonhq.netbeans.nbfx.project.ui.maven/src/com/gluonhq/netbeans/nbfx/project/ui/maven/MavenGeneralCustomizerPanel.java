package com.gluonhq.netbeans.nbfx.project.ui.maven;

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
 * The Maven "General" category: the POM's groupId, artifactId, version, name and packaging, stored
 * in {@code pom.xml}.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class MavenGeneralCustomizerPanel implements FxProjectCustomizerPanel {

    private TextField groupId;
    private TextField artifactId;
    private TextField version;
    private TextField name;
    private TextField packaging;
    private String initialGroupId;
    private String initialArtifactId;
    private String initialVersion;
    private String initialName;
    private String initialPackaging;

    @Override
    public String projectTypeId() {
        return "maven";
    }

    @Override
    public String id() {
        return "general";
    }

    @Override
    public String displayName() {
        return message("General.displayName");
    }

    @Override
    public int position() {
        return 100;
    }

    @Override
    public Node createPanel(Project project) {
        FileObject dir = project.getProjectDirectory();
        initialGroupId = MavenProjectFiles.pomEntry(dir, "groupId");
        initialArtifactId = MavenProjectFiles.pomEntry(dir, "artifactId");
        initialVersion = MavenProjectFiles.pomEntry(dir, "version");
        initialName = MavenProjectFiles.pomEntry(dir, "name");
        initialPackaging = MavenProjectFiles.pomEntry(dir, "packaging");
        groupId = new TextField(orEmpty(initialGroupId));
        artifactId = new TextField(orEmpty(initialArtifactId));
        version = new TextField(orEmpty(initialVersion));
        name = new TextField(orEmpty(initialName));
        packaging = new TextField(orEmpty(initialPackaging));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        grid.addRow(0, label("General.groupId"), groupId);
        grid.addRow(1, label("General.artifactId"), artifactId);
        grid.addRow(2, label("General.version"), version);
        grid.addRow(3, label("General.name"), name);
        grid.addRow(4, label("General.packaging"), packaging);
        return grid;
    }

    @Override
    public boolean isChanged() {
        return changed(initialGroupId, groupId) || changed(initialArtifactId, artifactId)
                || changed(initialVersion, version) || changed(initialName, name)
                || changed(initialPackaging, packaging);
    }

    @Override
    public void apply(Project project) {
        FileObject dir = project.getProjectDirectory();
        setIfChanged(dir, "groupId", initialGroupId, groupId);
        setIfChanged(dir, "artifactId", initialArtifactId, artifactId);
        setIfChanged(dir, "version", initialVersion, version);
        setIfChanged(dir, "name", initialName, name);
        setIfChanged(dir, "packaging", initialPackaging, packaging);
    }

    private static void setIfChanged(FileObject dir, String tag, String initial, TextField field) {
        if (changed(initial, field)) {
            MavenProjectFiles.setPomEntry(dir, tag, field.getText().trim());
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
        return NbBundle.getMessage(MavenGeneralCustomizerPanel.class, key);
    }
}

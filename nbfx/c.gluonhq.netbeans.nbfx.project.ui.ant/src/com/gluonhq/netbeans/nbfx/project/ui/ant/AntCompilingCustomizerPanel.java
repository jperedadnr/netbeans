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
 * The Ant / NetBeans-module "Build / Compiling" category: the Java source/target level, stored in
 * {@code nbproject/project.properties}.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class AntCompilingCustomizerPanel extends AntModuleCustomizerPanel {

    private TextField source;
    private TextField target;
    private String initialSource;
    private String initialTarget;

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "build.compiling";
    }

    @Override
    public String parentId() {
        return "build";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(AntCompilingCustomizerPanel.class, "Compiling.displayName");
    }

    @Override
    public int position() {
        return 100;
    }

    @Override
    public Node createPanel(Project project) {
        FileObject dir = project.getProjectDirectory();
        initialSource = AntProjectFiles.property(dir, "javac.source");
        initialTarget = AntProjectFiles.property(dir, "javac.target");
        source = new TextField(orEmpty(initialSource));
        target = new TextField(orEmpty(initialTarget));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        grid.addRow(0, label("Compiling.source"), source);
        grid.addRow(1, label("Compiling.target"), target);
        return grid;
    }

    @Override
    public boolean isChanged() {
        return changed(initialSource, source) || changed(initialTarget, target);
    }

    @Override
    public void apply(Project project) {
        FileObject dir = project.getProjectDirectory();
        if (changed(initialSource, source)) {
            AntProjectFiles.setProperty(dir, "javac.source", source.getText().trim());
        }
        if (changed(initialTarget, target)) {
            AntProjectFiles.setProperty(dir, "javac.target", target.getText().trim());
        }
    }

    private static boolean changed(String initial, TextField field) {
        return !orEmpty(initial).equals(field.getText().trim());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Label label(String key) {
        return new Label(NbBundle.getMessage(AntCompilingCustomizerPanel.class, key));
    }
}

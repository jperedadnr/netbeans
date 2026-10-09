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

/** Maven "Build / Compile" category: the compiler source/target level (POM properties). */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class MavenCompileCustomizerPanel implements FxProjectCustomizerPanel {

    private TextField source;
    private TextField target;
    private String initialSource;
    private String initialTarget;

    @Override
    public String projectTypeId() {
        return "maven";
    }

    @Override
    public String id() {
        return "build.compile";
    }

    @Override
    public String parentId() {
        return "build";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(MavenCompileCustomizerPanel.class, "Compile.displayName");
    }

    @Override
    public int position() {
        return 100;
    }

    @Override
    public Node createPanel(Project project) {
        FileObject dir = project.getProjectDirectory();
        initialSource = MavenProjectFiles.pomProperty(dir, "maven.compiler.source");
        initialTarget = MavenProjectFiles.pomProperty(dir, "maven.compiler.target");
        source = new TextField(initialSource == null ? "" : initialSource);
        target = new TextField(initialTarget == null ? "" : initialTarget);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        grid.addRow(0, new Label(NbBundle.getMessage(MavenCompileCustomizerPanel.class, "Compile.source")), source);
        grid.addRow(1, new Label(NbBundle.getMessage(MavenCompileCustomizerPanel.class, "Compile.target")), target);
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
            MavenProjectFiles.setPomProperty(dir, "maven.compiler.source", source.getText().trim());
        }
        if (changed(initialTarget, target)) {
            MavenProjectFiles.setPomProperty(dir, "maven.compiler.target", target.getText().trim());
        }
    }

    private static boolean changed(String initial, TextField field) {
        return !(initial == null ? "" : initial).equals(field.getText().trim());
    }
}

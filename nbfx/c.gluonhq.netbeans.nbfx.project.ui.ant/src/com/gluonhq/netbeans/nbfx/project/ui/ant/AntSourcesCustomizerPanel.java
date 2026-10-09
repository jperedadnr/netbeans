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
 * The Ant / NetBeans-module "Sources" category: the main and test source directories, stored in
 * {@code nbproject/project.properties}.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class AntSourcesCustomizerPanel extends AntModuleCustomizerPanel {

    private TextField srcDir;
    private TextField testSrcDir;
    private String initialSrc;
    private String initialTest;

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "sources";
    }

    @Override
    public String displayName() {
        return message("Sources.displayName");
    }

    @Override
    public int position() {
        return 100;
    }

    @Override
    public Node createPanel(Project project) {
        FileObject dir = project.getProjectDirectory();
        initialSrc = AntProjectFiles.property(dir, "src.dir");
        initialTest = AntProjectFiles.property(dir, "test.src.dir");
        srcDir = new TextField(orEmpty(initialSrc));
        testSrcDir = new TextField(orEmpty(initialTest));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        grid.addRow(0, label("Sources.srcDir"), srcDir);
        grid.addRow(1, label("Sources.testSrcDir"), testSrcDir);
        return grid;
    }

    @Override
    public boolean isChanged() {
        return changed(initialSrc, srcDir) || changed(initialTest, testSrcDir);
    }

    @Override
    public void apply(Project project) {
        FileObject dir = project.getProjectDirectory();
        if (changed(initialSrc, srcDir)) {
            AntProjectFiles.setProperty(dir, "src.dir", srcDir.getText().trim());
        }
        if (changed(initialTest, testSrcDir)) {
            AntProjectFiles.setProperty(dir, "test.src.dir", testSrcDir.getText().trim());
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
        return NbBundle.getMessage(AntSourcesCustomizerPanel.class, key);
    }
}

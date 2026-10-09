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
 * The Ant / NetBeans-module "Display" category: the module's name, category and descriptions, stored
 * in {@code manifest.mf}.
 */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class AntDisplayCustomizerPanel extends AntModuleCustomizerPanel {

    private TextField name;
    private TextField category;
    private TextField shortDescription;
    private TextField longDescription;
    private String initialName;
    private String initialCategory;
    private String initialShort;
    private String initialLong;

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "display";
    }

    @Override
    public String displayName() {
        return message("Display.displayName");
    }

    @Override
    public int position() {
        return 300;
    }

    @Override
    public Node createPanel(Project project) {
        FileObject dir = project.getProjectDirectory();
        initialName = AntProjectFiles.manifestEntry(dir, "OpenIDE-Module-Name");
        initialCategory = AntProjectFiles.manifestEntry(dir, "OpenIDE-Module-Display-Category");
        initialShort = AntProjectFiles.manifestEntry(dir, "OpenIDE-Module-Short-Description");
        initialLong = AntProjectFiles.manifestEntry(dir, "OpenIDE-Module-Long-Description");
        name = new TextField(orEmpty(initialName));
        category = new TextField(orEmpty(initialCategory));
        shortDescription = new TextField(orEmpty(initialShort));
        longDescription = new TextField(orEmpty(initialLong));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        grid.addRow(0, label("Display.name"), name);
        grid.addRow(1, label("Display.category"), category);
        grid.addRow(2, label("Display.shortDescription"), shortDescription);
        grid.addRow(3, label("Display.longDescription"), longDescription);
        return grid;
    }

    @Override
    public boolean isChanged() {
        return changed(initialName, name) || changed(initialCategory, category)
                || changed(initialShort, shortDescription) || changed(initialLong, longDescription);
    }

    @Override
    public void apply(Project project) {
        FileObject dir = project.getProjectDirectory();
        setIfChanged(dir, "OpenIDE-Module-Name", initialName, name);
        setIfChanged(dir, "OpenIDE-Module-Display-Category", initialCategory, category);
        setIfChanged(dir, "OpenIDE-Module-Short-Description", initialShort, shortDescription);
        setIfChanged(dir, "OpenIDE-Module-Long-Description", initialLong, longDescription);
    }

    private static void setIfChanged(FileObject dir, String key, String initial, TextField field) {
        if (changed(initial, field)) {
            AntProjectFiles.setManifestEntry(dir, key, field.getText().trim());
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
        return NbBundle.getMessage(AntDisplayCustomizerPanel.class, key);
    }
}

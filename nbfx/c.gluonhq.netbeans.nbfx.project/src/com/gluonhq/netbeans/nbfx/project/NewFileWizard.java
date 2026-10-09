/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.project;

import com.gluonhq.netbeans.nbfx.annotations.FxWizardRegistration;
import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.wizard.FxWizard;
import com.gluonhq.netbeans.nbfx.wizard.FxWizardPanel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Creates a Java class in a folder (defaulting to the navigator selection or the selected project's
 * main sources) and opens it in the editor.
 *
 * @since 1.0
 */
@FxWizardRegistration(id = NewFileWizard.ID, displayName = "Java Class",
        category = "File", position = 10)
public final class NewFileWizard implements FxWizard {

    /** The wizard id, referenced by the New File command. */
    public static final String ID = "newFile";

    private final TextField folder = new TextField(defaultFolder());
    private final TextField name = new TextField("NewClass");
    private final GridPane grid = new GridPane();
    private final FxWizardPanel page;

    /** Creates the wizard. Must run on the JavaFX Application Thread. */
    public NewFileWizard() {
        buildGrid();
        page = new FxWizardPanel() {
            @Override
            public String getTitle() {
                return message("NewFileWizard.page.title");
            }

            @Override
            public Node getComponent() {
                return grid;
            }

            @Override
            public boolean isValid() {
                return valid();
            }
        };
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return message("NewFileWizard.title");
    }

    @Override
    public String getCategory() {
        return "File";
    }

    @Override
    public List<FxWizardPanel> getPages() {
        return List.of(page);
    }

    @Override
    public void finish() {
        Path dir = Paths.get(folder.getText().trim());
        if (!Files.isDirectory(dir)) {
            ErrorReporter.report(message("NewFileWizard.error.title"),
                    message("NewFileWizard.error.header"), "Not a folder: " + dir);
            return;
        }
        String simple = name.getText().trim();
        Path file = dir.resolve(simple + ".java");
        if (Files.exists(file)) {
            ErrorReporter.report(message("NewFileWizard.error.title"),
                    message("NewFileWizard.error.header"), "The file already exists: " + file);
            return;
        }
        try {
            Files.writeString(file, source(packageOf(dir), simple));
            open(file);
        } catch (IOException ex) {
            ErrorReporter.report(message("NewFileWizard.error.title"),
                    message("NewFileWizard.error.header"), ex.getMessage(), ex);
        }
    }

    private void buildGrid() {
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(4));
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields, new ColumnConstraints());

        Button browse = new Button(message("NewFileWizard.browse"));
        browse.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var file = chooser.showDialog(grid.getScene() == null ? null : grid.getScene().getWindow());
            if (file != null) {
                folder.setText(file.getAbsolutePath());
            }
        });
        grid.addRow(0, label("NewFileWizard.folder"), folder, browse);
        grid.addRow(1, label("NewFileWizard.name"), name);
    }

    private boolean valid() {
        String simple = name.getText().trim();
        return !simple.isEmpty() && simple.equals(identifier(simple))
                && !folder.getText().trim().isEmpty();
    }

    private static void open(Path file) {
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        FileObject fileObject = FileUtil.toFileObject(file.toFile());
        if (contentManager != null && fileObject != null) {
            contentManager.openFile(fileObject, null);
        }
    }

    private static String source(String pkg, String simple) {
        String packageLine = pkg.isEmpty() ? "" : "package " + pkg + ";\n\n";
        return packageLine + "public class " + simple + " {\n}\n";
    }

    /** The package of {@code dir} when it is under {@code src/main/java}, or an empty string. */
    private static String packageOf(Path dir) {
        String path = dir.toString().replace('\\', '/');
        String marker = "/src/main/java/";
        int index = path.lastIndexOf(marker);
        if (index < 0) {
            return "";
        }
        return path.substring(index + marker.length()).replace('/', '.');
    }

    private static String defaultFolder() {
        FileSelectionContext selection = Lookup.getDefault().lookup(FileSelectionContext.class);
        if (selection != null) {
            List<FileObject> files = selection.selectedFiles().getValue();
            if (files != null && !files.isEmpty()) {
                FileObject first = files.get(0);
                FileObject directory = first.isFolder() ? first : first.getParent();
                if (directory != null && FileUtil.toFile(directory) != null) {
                    return FileUtil.toFile(directory).getAbsolutePath();
                }
            }
        }
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject project = registry == null ? null : registry.getSelected();
        if (project != null) {
            Path source = Paths.get(project.getPath(), "src", "main", "java");
            if (Files.isDirectory(source)) {
                return source.toString();
            }
            return project.getPath();
        }
        String home = System.getProperty("user.home");
        return home == null || home.isBlank() ? "." : home;
    }

    /** Keeps only Java-identifier characters, so a name becomes a valid class name. */
    private static String identifier(String value) {
        StringBuilder builder = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isJavaIdentifierPart(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static Label label(String key) {
        return new Label(message(key));
    }

    private static String message(String key) {
        return NbBundle.getMessage(NewFileWizard.class, key);
    }
}

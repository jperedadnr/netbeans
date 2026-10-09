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
import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
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
 * Creates a Java application built with Maven: a {@code pom.xml}, a {@code src/main/java} tree with
 * a {@code main} class, and opens the project. A single page asks for the location, name, group id
 * and package.
 *
 * @since 1.0
 */
@FxWizardRegistration(id = NewProjectWizard.ID, displayName = "Java with Maven",
        category = "Project", position = 10)
public final class NewProjectWizard implements FxWizard {

    /** The wizard id, referenced by the New Project command. */
    public static final String ID = "newProject";

    private final TextField location = new TextField(defaultLocation());
    private final TextField name = new TextField("MyApp");
    private final TextField group = new TextField("com.example");
    private final TextField packageName = new TextField();
    private boolean packageEdited;
    private final GridPane grid = new GridPane();
    private final FxWizardPanel page;

    /** Creates the wizard. Must run on the JavaFX Application Thread. */
    public NewProjectWizard() {
        name.textProperty().addListener((observable, old, now) -> updatePackage());
        group.textProperty().addListener((observable, old, now) -> updatePackage());
        packageName.textProperty().addListener((observable, old, now) -> packageEdited = true);
        buildGrid();
        updatePackage();
        page = new FxWizardPanel() {
            @Override
            public String getTitle() {
                return message("NewProjectWizard.page.title");
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
        return message("NewProjectWizard.title");
    }

    @Override
    public String getCategory() {
        return "Project";
    }

    @Override
    public List<FxWizardPanel> getPages() {
        return List.of(page);
    }

    @Override
    public void finish() {
        Path root = Paths.get(location.getText().trim()).resolve(name.getText().trim());
        if (Files.exists(root) && containsFiles(root)) {
            ErrorReporter.report(message("NewProjectWizard.error.title"),
                    message("NewProjectWizard.error.header"),
                    "The folder already exists and is not empty: " + root);
            return;
        }
        String pkg = packageName.getText().trim();
        String artifact = identifier(name.getText().trim());
        try {
            Path source = root.resolve("src/main/java");
            if (!pkg.isEmpty()) {
                source = source.resolve(pkg.replace('.', '/'));
            }
            Files.createDirectories(source);
            Files.writeString(root.resolve("pom.xml"), pom(artifact, pkg));
            Files.writeString(source.resolve("App.java"), app(pkg));
            open(root);
        } catch (IOException ex) {
            ErrorReporter.report(message("NewProjectWizard.error.title"),
                    message("NewProjectWizard.error.header"), ex.getMessage(), ex);
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

        Button browse = new Button(message("NewProjectWizard.browse"));
        browse.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var file = chooser.showDialog(grid.getScene() == null ? null : grid.getScene().getWindow());
            if (file != null) {
                location.setText(file.getAbsolutePath());
            }
        });
        grid.addRow(0, label("NewProjectWizard.location"), location, browse);
        grid.addRow(1, label("NewProjectWizard.name"), name);
        grid.addRow(2, label("NewProjectWizard.group"), group);
        grid.addRow(3, label("NewProjectWizard.package"), packageName);
    }

    private void updatePackage() {
        if (packageEdited) {
            return;
        }
        String groupId = group.getText().trim();
        String artifact = identifier(name.getText().trim()).toLowerCase();
        packageName.setText(groupId.isEmpty() ? artifact : groupId + "." + artifact);
    }

    private boolean valid() {
        String projectName = name.getText().trim();
        if (projectName.isEmpty() || identifier(projectName).isEmpty() || location.getText().trim().isEmpty()) {
            return false;
        }
        String pkg = packageName.getText().trim();
        if (pkg.isEmpty()) {
            return true;
        }
        for (String segment : pkg.split("\\.")) {
            if (!segment.equals(identifier(segment))) {
                return false;
            }
        }
        return true;
    }

    private static void open(Path root) {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        FileObject fileObject = FileUtil.toFileObject(root.toFile());
        if (registry != null && fileObject != null) {
            registry.open(fileObject);
        }
    }

    private String pom(String artifact, String pkg) {
        String mainClass = pkg.isEmpty() ? "App" : pkg + ".App";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                        <maven.compiler.release>25</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.codehaus.mojo</groupId>
                                <artifactId>exec-maven-plugin</artifactId>
                                <version>3.5.0</version>
                                <configuration>
                                    <mainClass>%s</mainClass>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(group.getText().trim(), artifact, mainClass);
    }

    private static String app(String pkg) {
        String packageLine = pkg.isEmpty() ? "" : "package " + pkg + ";\n\n";
        return packageLine + "public class App {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello, World!\");\n"
                + "    }\n"
                + "}\n";
    }

    private static boolean containsFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isPresent();
        } catch (IOException ex) {
            return false;
        }
    }

    private static String defaultLocation() {
        String home = System.getProperty("user.home");
        return home == null || home.isBlank() ? "." : home;
    }

    /** Keeps only Java-identifier characters, so a name becomes a valid artifact/package segment. */
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
        return NbBundle.getMessage(NewProjectWizard.class, key);
    }
}

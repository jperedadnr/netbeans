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
package com.gluonhq.netbeans.nbfx.plugins;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.netbeans.api.autoupdate.InstallSupport;
import org.netbeans.api.autoupdate.OperationContainer;
import org.netbeans.api.autoupdate.UpdateElement;
import org.netbeans.api.autoupdate.UpdateUnitProvider;
import org.netbeans.api.autoupdate.UpdateUnitProviderFactory;
import org.openide.util.NbBundle;

/**
 * The Plugins dialog, the JavaFX counterpart of NetBeans' Tools ▸ Plugins: tabs for Updates,
 * Available Plugins, Installed and Settings, each a filtered, checkable table with the operations of
 * that tab. Operations run through the headless autoupdate API; when a change requires a restart it
 * is applied at the next start.
 *
 * @since 1.0
 */
public final class PluginsDialog {

    private static final int WIDTH = 820;
    private static final int HEIGHT = 560;

    private enum Kind {
        UPDATES, AVAILABLE, INSTALLED
    }

    private PluginsDialog() {
    }

    /** Shows the Plugins dialog. Must run on the JavaFX Application Thread. */
    public static void show() {
        PluginModel model = new PluginModel();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(message("Plugins.title"));
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefSize(WIDTH, HEIGHT);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                unitTab(model, model.updates(), Kind.UPDATES),
                unitTab(model, model.available(), Kind.AVAILABLE),
                unitTab(model, model.installed(), Kind.INSTALLED),
                settingsTab());

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(44, 44);
        spinner.visibleProperty().bind(model.loadingProperty());

        StackPane root = new StackPane(tabs, spinner);
        pane.setContent(root);

        model.load(null);
        dialog.showAndWait();
    }

    private static Tab unitTab(PluginModel model, ObservableList<PluginRow> rows, Kind kind) {
        Tab tab = new Tab(title(kind));
        tab.setClosable(false);

        FilteredList<PluginRow> filtered = new FilteredList<>(rows, row -> true);
        TableView<PluginRow> table = new TableView<>(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<PluginRow, Boolean> check = new TableColumn<>("");
        check.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        check.setCellFactory(CheckBoxTableCell.forTableColumn(check));
        check.setEditable(true);
        check.setSortable(false);
        check.setMaxWidth(34);
        check.setMinWidth(34);
        table.getColumns().add(check);
        table.getColumns().add(textColumn("Plugins.column.name", PluginRow::name));
        table.getColumns().add(textColumn("Plugins.column.category", PluginRow::category));
        table.getColumns().add(textColumn("Plugins.column.version", PluginRow::version));
        if (kind != Kind.AVAILABLE) {
            table.getColumns().add(textColumn("Plugins.column.installed", PluginRow::installedVersion));
        }
        table.setEditable(true);

        TextField filter = new TextField();
        filter.setPromptText(message("Plugins.filter"));
        filter.textProperty().addListener((observable, old, text) -> {
            String needle = text == null ? "" : text.trim().toLowerCase();
            filtered.setPredicate(row -> needle.isEmpty()
                    || row.name().toLowerCase().contains(needle)
                    || row.category().toLowerCase().contains(needle));
        });

        ToolBar actions = new ToolBar(filter);
        switch (kind) {
            case UPDATES -> {
                Button update = new Button(message("Plugins.action.update"));
                update.setOnAction(event -> installOrUpdate(model,
                        OperationContainer.createForUpdate(), selected(rows)));
                Button updateAll = new Button(message("Plugins.action.updateAll"));
                updateAll.setOnAction(event -> installOrUpdate(model,
                        OperationContainer.createForUpdate(), candidates(rows)));
                actions.getItems().addAll(update, updateAll);
            }
            case AVAILABLE -> {
                Button install = new Button(message("Plugins.action.install"));
                install.setOnAction(event -> installOrUpdate(model,
                        OperationContainer.createForInstall(), selected(rows)));
                actions.getItems().add(install);
            }
            case INSTALLED -> {
                Button activate = new Button(message("Plugins.action.activate"));
                activate.setOnAction(event -> PluginOperations.enable(
                        OperationContainer.createForEnable(), selected(rows), () -> model.load(null)));
                Button deactivate = new Button(message("Plugins.action.deactivate"));
                deactivate.setOnAction(event -> PluginOperations.disable(
                        OperationContainer.createForDisable(), selected(rows), () -> model.load(null)));
                Button uninstall = new Button(message("Plugins.action.uninstall"));
                uninstall.setOnAction(event -> {
                    List<UpdateElement> elements = selected(rows);
                    if (elements.isEmpty() || PluginDialogs.confirm(message("Plugins.confirm.uninstall"))) {
                        PluginOperations.uninstall(
                                OperationContainer.createForUninstall(), elements, () -> model.load(null));
                    }
                });
                actions.getItems().addAll(activate, deactivate, uninstall);
            }
        }

        VBox content = new VBox(actions, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private static void installOrUpdate(PluginModel model,
            OperationContainer<InstallSupport> container, List<UpdateElement> elements) {
        if (elements.isEmpty()) {
            return;
        }
        PluginOperations.installOrUpdate(container, elements, () -> model.load(null));
    }

    private static Tab settingsTab() {
        Tab tab = new Tab(message("Plugins.tab.settings"));
        tab.setClosable(false);

        UpdateUnitProviderFactory factory = UpdateUnitProviderFactory.getDefault();
        ObservableList<ProviderRow> providers = FXCollections.observableArrayList();
        for (UpdateUnitProvider provider : factory.getUpdateUnitProviders(false)) {
            providers.add(new ProviderRow(provider));
        }

        TableView<ProviderRow> table = new TableView<>(providers);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<ProviderRow, Boolean> enabled = new TableColumn<>(message("Plugins.column.enabled"));
        enabled.setCellValueFactory(cell -> cell.getValue().enabled());
        enabled.setCellFactory(CheckBoxTableCell.forTableColumn(enabled));
        enabled.setEditable(true);
        enabled.setMaxWidth(90);
        table.getColumns().add(enabled);
        table.getColumns().add(providerColumn("Plugins.column.name", row -> row.provider().getDisplayName()));
        table.getColumns().add(providerColumn("Plugins.column.url",
                row -> row.provider().getProviderURL() == null ? "" : row.provider().getProviderURL().toString()));
        table.setEditable(true);

        Button add = new Button(message("Plugins.action.addCenter"));
        add.setOnAction(event -> addProvider(factory, providers, table));
        Button remove = new Button(message("Plugins.action.removeCenter"));
        remove.setOnAction(event -> {
            ProviderRow row = table.getSelectionModel().getSelectedItem();
            if (row != null) {
                try {
                    factory.remove(row.provider());
                    providers.remove(row);
                } catch (RuntimeException ex) {
                    PluginDialogs.error(ex.getMessage());
                }
            }
        });

        VBox content = new VBox(new ToolBar(add, remove), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private static void addProvider(UpdateUnitProviderFactory factory,
            ObservableList<ProviderRow> providers, TableView<ProviderRow> table) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(message("Plugins.addCenter.title"));
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField name = new TextField();
        TextField url = new TextField();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label(message("Plugins.addCenter.name")), name);
        grid.addRow(1, new Label(message("Plugins.addCenter.url")), url);
        pane.setContent(grid);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK || name.getText().isBlank() || url.getText().isBlank()) {
                return;
            }
            try {
                URL providerUrl = URI.create(url.getText().trim()).toURL();
                UpdateUnitProvider provider = factory.create(name.getText().trim(), name.getText().trim(), providerUrl);
                ProviderRow row = new ProviderRow(provider);
                providers.add(row);
                table.getSelectionModel().select(row);
            } catch (RuntimeException | java.net.MalformedURLException ex) {
                PluginDialogs.error(ex.getMessage());
            }
        });
    }

    private static List<UpdateElement> selected(ObservableList<PluginRow> rows) {
        return rows.stream().filter(PluginRow::isSelected).map(PluginRow::candidate).toList();
    }

    private static List<UpdateElement> candidates(ObservableList<PluginRow> rows) {
        return rows.stream().map(PluginRow::candidate).toList();
    }

    private static TableColumn<PluginRow, String> textColumn(String headerKey, Function<PluginRow, String> value) {
        TableColumn<PluginRow, String> column = new TableColumn<>(message(headerKey));
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private static TableColumn<ProviderRow, String> providerColumn(String headerKey,
            Function<ProviderRow, String> value) {
        TableColumn<ProviderRow, String> column = new TableColumn<>(message(headerKey));
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private static String title(Kind kind) {
        return switch (kind) {
            case UPDATES -> message("Plugins.tab.updates");
            case AVAILABLE -> message("Plugins.tab.available");
            case INSTALLED -> message("Plugins.tab.installed");
        };
    }

    private static String message(String key) {
        return NbBundle.getMessage(PluginsDialog.class, key);
    }

    /** An update center row; toggling {@code enabled} enables or disables the provider. */
    private record ProviderRow(UpdateUnitProvider provider, BooleanProperty enabled) {

        ProviderRow(UpdateUnitProvider provider) {
            this(provider, createEnabled(provider));
        }

        private static BooleanProperty createEnabled(UpdateUnitProvider provider) {
            BooleanProperty property = new SimpleBooleanProperty(provider.isEnabled());
            property.addListener((observable, was, now) -> provider.setEnable(now));
            return property;
        }
    }
}

package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.api.NavigatorProvider;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchScope;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The <b>Scope</b> combo of the search form: Open Projects, the selected project, Open Files, the
 * folders selected in a tree (when the dialog was opened from one) and <em>Browse...</em>, which
 * asks for a folder and then names it. The kind last searched is preselected when still available.
 * Every entry is drawn with its icon, as NetBeans' {@code ScopeCellRenderer} draws
 * {@code SearchScopeDefinition.getIcon()}.
 */
final class ScopeChooser extends ComboBox<ScopeChooser.Item> {

    /** One entry of the combo; the Browse entry changes its scope every time a folder is picked. */
    static final class Item {

        private final String id;
        private SearchScope scope;

        Item(SearchScope scope) {
            this.id = scope.id();
            this.scope = scope;
        }

        Item(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        /** The scope, {@code null} for Browse before a folder was picked. */
        SearchScope scope() {
            return scope;
        }

        String label() {
            return scope == null ? message("SCOPE_Browse") : scope.label();
        }

        /**
         * The icon shown left of the label: the resource of this kind of scope, and for the current
         * project the icon the Projects view gives its root node - the folder icon when no
         * navigator knows the project.
         */
        Node icon() {
            if (scope instanceof SearchScope.CurrentProject(OpenProject project)) {
                Node projectIcon = projectIcon(project.getRoot());
                if (projectIcon != null) {
                    return projectIcon;
                }
            }
            return switch (id) {
                case SearchScope.ID_OPEN_PROJECTS -> view("all_projects.png");
                case SearchScope.ID_OPEN_FILES -> view("multi_selection.png");
                // the current project (without a navigator icon), the selected folders and Browse...
                default -> SearchIcons.folder();
            };
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** Edge length, in pixels, the scope icons are designed for. */
    private static final double ICON_SIZE = 16;

    private static final Map<String, Image> ICONS = new ConcurrentHashMap<>();

    private final Item browse = new Item(SearchScope.ID_BROWSE);
    private Supplier<Window> owner = () -> null;
    private Item previous;
    /** Set while the selection is changed by code, when Browse must not ask for a folder. */
    private boolean selecting;

    ScopeChooser() {
        getStyleClass().add("scope-chooser");
        setMaxWidth(Double.MAX_VALUE);
        setConverter(new StringConverter<>() {
            @Override
            public String toString(Item item) {
                return item == null ? "" : item.label();
            }

            @Override
            public Item fromString(String string) {
                return null;
            }
        });
        setCellFactory(_ -> new ScopeCell());
        setButtonCell(new ScopeCell());
        getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item == browse && !selecting) {
                pickFolder(old);
            } else if (item != null) {
                previous = item;
            }
        });
    }

    /** The window the directory chooser is attached to. */
    void setOwner(Supplier<Window> owner) {
        this.owner = Objects.requireNonNull(owner);
    }

    /**
     * Fills the combo for a dialog opened with {@code folders} selected in a tree ({@code null} or
     * empty when it was not) and selects those folders, else the kind with {@code preferredId},
     * else the first entry.
     */
    void populate(List<FileObject> folders, String preferredId) {
        List<Item> items = new ArrayList<>();
        items.add(new Item(SearchScope.OpenProjects.current()));
        SearchScope.CurrentProject current = SearchScope.CurrentProject.current();
        if (current != null) {
            items.add(new Item(current));
        }
        items.add(new Item(SearchScope.OpenFiles.current()));
        Item selected = null;
        if (folders != null && !folders.isEmpty()) {
            selected = new Item(new SearchScope.Folders(folders));
            items.add(selected);
        }
        items.add(browse);
        getItems().setAll(items);
        Item preferred = selected != null ? selected : items.stream()
                .filter(item -> item != browse && item.id().equals(preferredId))
                .findFirst()
                .orElse(items.getFirst());
        previous = preferred;
        getSelectionModel().select(preferred);
    }

    /** Selects the entry for {@code scope}: the same kind, or a Browse entry set to its folders. */
    void select(SearchScope scope) {
        selecting = true;
        try {
            if (scope instanceof SearchScope.Browse) {
                browse.scope = scope;
                previous = browse;
                getSelectionModel().select(browse);
                return;
            }
            getItems().stream()
                    .filter(item -> item != browse && item.id().equals(scope.id()))
                    .findFirst()
                    .ifPresent(item -> getSelectionModel().select(item));
        } finally {
            selecting = false;
        }
    }

    /** The scope selected, {@code null} when Browse is selected without a folder. */
    SearchScope getScope() {
        Item item = getValue();
        return item == null ? null : item.scope();
    }

    private void pickFolder(Item fallback) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(message("TITLE_BrowseFolder"));
        if (browse.scope instanceof SearchScope.Browse(List<FileObject> roots) && !roots.isEmpty()) {
            File initial = FileUtil.toFile(roots.getFirst());
            if (initial != null && initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
        }
        File chosen = chooser.showDialog(owner.get());
        if (chosen == null) {
            if (browse.scope == null) {
                getSelectionModel().select(fallback != null ? fallback : previous);
            }
            return;
        }
        FileObject folder = FileUtil.toFileObject(FileUtil.normalizeFile(chosen));
        if (folder == null) {
            getSelectionModel().select(fallback != null ? fallback : previous);
            return;
        }
        browse.scope = new SearchScope.Browse(List.of(folder));
        previous = browse;
        // the label changed: make the button cell show it
        selecting = true;
        try {
            getSelectionModel().clearSelection();
            getSelectionModel().select(browse);
        } finally {
            selecting = false;
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(ScopeChooser.class, key);
    }

    /** A fixed-size view of the (cached) icon {@code name} of this package. */
    private static ImageView view(String name) {
        Image image = ICONS.computeIfAbsent(name, icon ->
                new Image(Objects.requireNonNull(ScopeChooser.class.getResource(icon),
                        () -> "Missing icon " + icon).toExternalForm()));
        ImageView view = new ImageView(image);
        view.setFitWidth(ICON_SIZE);
        view.setFitHeight(ICON_SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    /**
     * The icon the Projects view shows for the project rooted at {@code root} - its project type -,
     * or {@code null} when no {@link NavigatorProvider} has that project loaded.
     */
    private static Node projectIcon(FileObject root) {
        for (NavigatorProvider navigator : Lookup.getDefault().lookupAll(NavigatorProvider.class)) {
            String iconName = navigator.getProjectIconName(root);
            Node icon = iconName == null ? null : navigator.getProjectIcon(root, iconName);
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }

    /** An entry drawn as its icon plus its label; used for the list and for the combo's button. */
    private static final class ScopeCell extends ListCell<Item> {

        private final Label label;
        private final HBox box;

        ScopeCell() {
            label = new Label();
            label.getStyleClass().add("scope-label");
            box = new HBox(4, label);
            box.getStyleClass().add("scope-box");
            box.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(Item item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            label.setText(item.label());
            box.getChildren().setAll(List.of(item.icon(), label));
            setGraphic(box);
        }
    }
}

package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.api.NavigatorProvider;
import com.gluonhq.netbeans.nbfx.api.file.FileIconProvider;
import com.gluonhq.netbeans.nbfx.api.icons.ToolIcons;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * The icons of the Search view: the row markers and the find glyph copied from NetBeans'
 * {@code api.search} module (in {@code icons/}), the tool bar glyphs and the folder shared through
 * {@link ToolIcons}, the scope icons next to the dialog classes, the project icons the Projects
 * view gives its roots, and file icons from the registered {@link FileIconProvider}.
 */
final class SearchIcons {

    static final double SIZE = 16;

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private SearchIcons() {
    }

    /** The (cached) image of this module's icon {@code icons/<name>.png}, or of the shared {@link ToolIcons} glyph of that name. */
    static Image image(String name) {
        return CACHE.computeIfAbsent(name, SearchIcons::load);
    }

    /** A fixed-size view of the icon {@code name}, as {@link #image(String)}. */
    static ImageView view(String name) {
        return fit(new ImageView(image(name)));
    }

    /** The folder icon the scope combo and the results tree show for folders. */
    static ImageView folder() {
        return ToolIcons.view(ToolIcons.FOLDER);
    }

    /**
     * The icon the Projects view shows for the project rooted at {@code root} - its project type -
     * or the folder icon when no navigator has the project loaded.
     */
    static Node projectIcon(FileObject root) {
        if (root != null) {
            for (NavigatorProvider navigator : Lookup.getDefault().lookupAll(NavigatorProvider.class)) {
                String iconName = navigator.getProjectIconName(root);
                Node icon = iconName == null ? null : navigator.getProjectIcon(root, iconName);
                if (icon != null) {
                    return icon;
                }
            }
        }
        return folder();
    }

    /** The registered file icon of {@code file}, or the folder icon for a folder, or {@code null} without a provider. */
    static Node fileIcon(FileObject file) {
        if (file == null) {
            return null;
        }
        FileIconProvider provider = Lookup.getDefault().lookup(FileIconProvider.class);
        Node icon = provider == null ? null : provider.createIcon(file);
        if (icon == null && file.isFolder()) {
            return folder();
        }
        return icon;
    }

    private static ImageView fit(ImageView view) {
        view.setFitWidth(SIZE);
        view.setFitHeight(SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    private static Image load(String name) {
        URL local = SearchIcons.class.getResource("icons/" + name + ".png");
        if (local != null) {
            return new Image(local.toExternalForm());
        }
        return Objects.requireNonNull(ToolIcons.image(name), () -> "Missing icon " + name);
    }
}

package com.gluonhq.netbeans.nbfx.api.icons;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * The tool bar and tree glyphs the result views share - Find Usages, Find in Projects - copied
 * from NetBeans (Apache 2.0): refresh, stop, previous / next match, expand / collapse all, the
 * logical / physical view pair, the preview toggle, the "edit parameters" pencil, and the plain
 * folder. Each is a 16 pixel PNG in this package; {@link #view(String)} gives a fresh node, as a
 * scene graph node has one parent.
 */
public final class ToolIcons {

    public static final String REFRESH = "refresh";
    public static final String STOP = "stop";
    public static final String PREVIOUS_MATCH = "prevmatch";
    public static final String NEXT_MATCH = "nextmatch";
    public static final String EXPAND_TREE = "expandTree";
    public static final String COLLAPSE_TREE = "collapseTree";
    public static final String LOGICAL_VIEW = "logical_view";
    public static final String FILE_VIEW = "file_view";
    public static final String PREVIEW = "preview";
    public static final String EDIT_PARAMETERS = "edit_parameters";
    public static final String FOLDER = "defaultFolder";

    /** Edge length, in pixels, the icons are designed for. */
    public static final double SIZE = 16;

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private ToolIcons() {
    }

    /**
     * The (cached) image of the shared icon {@code name}.
     *
     * @throws NullPointerException for a name that is not a shared icon
     */
    public static Image image(String name) {
        return CACHE.computeIfAbsent(name, ToolIcons::load);
    }

    /** A {@link #SIZE}-pixel view of the shared icon {@code name}. */
    public static ImageView view(String name) {
        ImageView view = new ImageView(image(name));
        view.setFitWidth(SIZE);
        view.setFitHeight(SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    private static Image load(String name) {
        return new Image(Objects.requireNonNull(ToolIcons.class.getResource(name + ".png"),
                () -> "Missing shared icon " + name).toExternalForm());
    }
}

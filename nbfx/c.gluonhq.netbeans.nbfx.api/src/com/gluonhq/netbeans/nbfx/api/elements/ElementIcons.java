package com.gluonhq.netbeans.nbfx.api.elements;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps the shared source-element vocabulary ({@link SourceElementKind},
 * {@link SourceTypeKind} and {@link Modifier} bits) onto the icon images shipped
 * in this package, so the completion popup, the breadcrumbs bar and the
 * breadcrumbs popup all render the same glyphs.
 *
 * <p>Member icons (method / constructor / field) come in visibility variants
 * (public, private, protected, package-private) and, for methods and fields,
 * static variants. Kinds without a glyph (statements, separators) map to
 * {@code null}.</p>
 */
public final class ElementIcons {

    /** Edge length, in pixels, the shipped icons are designed for. */
    public static final double ICON_SIZE = 16;

    private static final Map<String, Image> ICON_CACHE = new ConcurrentHashMap<>();

    private ElementIcons() {
    }

    /**
     * Returns the (cached) icon for an element, or {@code null} when the kind has no glyph.
     *
     * @param kind general semantic category
     * @param typeKind detailed hint for {@link SourceElementKind#TYPE} elements and constructors
     * @param modifiers {@link Modifier} bits selecting visibility / static variants
     * @return shared icon image, or {@code null}
     */
    public static Image iconFor(SourceElementKind kind, SourceTypeKind typeKind, int modifiers) {
        String iconName = iconNameFor(kind, typeKind, modifiers);
        return iconName == null ? null : ICON_CACHE.computeIfAbsent(iconName, ElementIcons::loadIcon);
    }

    /**
     * Returns a fixed-size {@link ImageView} showing the element's icon, or {@code null}
     * when the kind has no glyph.
     *
     * @param kind general semantic category
     * @param typeKind detailed hint for {@link SourceElementKind#TYPE} elements and constructors
     * @param modifiers {@link Modifier} bits selecting visibility / static variants
     * @return new image view, or {@code null}
     */
    public static ImageView iconViewFor(SourceElementKind kind, SourceTypeKind typeKind, int modifiers) {
        Image icon = iconFor(kind, typeKind, modifiers);
        if (icon == null) {
            return null;
        }
        ImageView view = new ImageView(icon);
        view.setFitWidth(ICON_SIZE);
        view.setFitHeight(ICON_SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    /** Maps an element kind / type-kind to the icon file shipped in this package. */
    public static String iconNameFor(SourceElementKind kind, SourceTypeKind typeKind, int modifiers) {
        SourceTypeKind type = typeKind == null ? SourceTypeKind.OTHER : typeKind;
        return switch (kind) {
            case METHOD -> type == SourceTypeKind.CONSTRUCTOR
                    ? memberIcon("constructor", modifiers)
                    : memberIcon("method", modifiers);
            case FIELD -> memberIcon("field", modifiers);
            case PACKAGE -> "package.png";
            case MODULE -> "module.png";
            case TYPE -> switch (type) {
                case INTERFACE -> "interface.png";
                case ENUM -> "enum.png";
                case RECORD -> "record.png";
                case ANNOTATION, CLASS, OTHER, CONSTRUCTOR -> "class_16.png";
            };
            case VARIABLE, KEYWORD -> "localVariable.png";
            case TAG -> "tag-16.png";
            case RULE -> "style_sheet_16.png";
            case SEPARATOR, OTHER -> null;
        };
    }

    /** Builds the field / method / constructor icon name from {@code modifiers} (visibility + static flag). */
    private static String memberIcon(String base, int modifiers) {
        String accessSuffix = accessSuffix(modifiers);
        if ("constructor".equals(base)) {
            return "constructor" + accessSuffix + "_16.png";
        }
        if (Modifier.isStatic(modifiers)) {
            return base + "_static" + accessSuffix + "_16.png";
        }
        return base + accessSuffix + "_16.png";
    }

    /** Maps Java reflection access bits onto the icon-name suffix (private / protected / package-private). */
    private static String accessSuffix(int modifiers) {
        if (Modifier.isPrivate(modifiers)) {
            return "_private";
        }
        if (Modifier.isProtected(modifiers)) {
            return "_protected";
        }
        if (Modifier.isPublic(modifiers)) {
            return "";
        }
        return "_package_private";
    }

    private static Image loadIcon(String iconName) {
        try {
            return new Image(Objects.requireNonNull(ElementIcons.class.getResource(iconName)).toExternalForm());
        } catch (Exception ex) {
            return null;
        }
    }
}

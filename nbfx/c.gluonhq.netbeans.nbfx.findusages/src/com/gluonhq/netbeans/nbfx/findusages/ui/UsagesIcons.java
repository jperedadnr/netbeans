package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.api.NavigatorProvider;
import com.gluonhq.netbeans.nbfx.api.elements.ElementIcons;
import com.gluonhq.netbeans.nbfx.api.elements.SourceElementKind;
import com.gluonhq.netbeans.nbfx.api.elements.SourceModifiers;
import com.gluonhq.netbeans.nbfx.api.elements.SourceTypeKind;
import com.gluonhq.netbeans.nbfx.api.file.FileIconProvider;
import com.gluonhq.netbeans.nbfx.api.icons.ToolIcons;
import com.gluonhq.netbeans.nbfx.findusages.model.Access;
import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * The icons of the Usages view: the result markers copied from NetBeans' refactoring modules (in
 * {@code icons/}), the tool bar glyphs shared through {@link ToolIcons}, the element glyphs shared
 * through {@link ElementIcons}, and file icons from the registered {@link FileIconProvider}.
 */
final class UsagesIcons {

    static final double SIZE = 16;

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private UsagesIcons() {
    }

    /** The (cached) image {@code icons/<name>.png}. */
    static Image image(String name) {
        return CACHE.computeIfAbsent(name, UsagesIcons::load);
    }

    /** A fixed-size view of the icon {@code icons/<name>.png}. */
    static ImageView view(String name) {
        ImageView view = new ImageView(image(name));
        view.setFitWidth(SIZE);
        view.setFitHeight(SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    /**
     * The result marker of a usage: comment, import, or read / write / read-write access for Java;
     * the plain orange marker NetBeans' CSS refactoring uses for anything else.
     */
    static String usageIconName(Usage usage) {
        if (!"java".equalsIgnoreCase(usage.file().getExt())) {
            return "found_item_orange";
        }
        if (usage.inComment()) {
            return "found_item_comment";
        }
        if (usage.inImport()) {
            return "found_item_import";
        }
        Access access = usage.access();
        if (access == Access.WRITE) {
            return "found_item_write";
        }
        if (access == Access.READ_WRITE) {
            return "found_item_readwrite";
        }
        return "found_item_read";
    }

    /** The element glyph of a type / method / field / variable, or {@code null} for kinds without one. */
    static ImageView elementIcon(ElementKind kind, Set<Modifier> modifiers) {
        SourceElementKind sourceKind = sourceKind(kind);
        if (sourceKind == null) {
            return null;
        }
        return ElementIcons.iconViewFor(sourceKind, typeKind(kind), SourceModifiers.toModifierBits(modifiers));
    }

    static ImageView packageIcon() {
        return ElementIcons.iconViewFor(SourceElementKind.PACKAGE, null, 0);
    }

    /**
     * The icon the Projects view shows for the project rooted at {@code projectRoot}, falling back
     * to its file icon when no navigator knows the project.
     */
    static Node projectIcon(FileObject projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        for (NavigatorProvider navigator : Lookup.getDefault().lookupAll(NavigatorProvider.class)) {
            String iconName = navigator.getProjectIconName(projectRoot);
            Node icon = iconName == null ? null : navigator.getProjectIcon(projectRoot, iconName);
            if (icon != null) {
                return icon;
            }
        }
        return fileIcon(projectRoot);
    }

    /** The registered file icon of {@code file} (a folder icon for roots), or {@code null} without a provider. */
    static Node fileIcon(FileObject file) {
        if (file == null) {
            return null;
        }
        FileIconProvider provider = Lookup.getDefault().lookup(FileIconProvider.class);
        return provider == null ? null : provider.createIcon(file);
    }

    private static SourceElementKind sourceKind(ElementKind kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> SourceElementKind.TYPE;
            case METHOD, CONSTRUCTOR -> SourceElementKind.METHOD;
            case FIELD, ENUM_CONSTANT, RECORD_COMPONENT -> SourceElementKind.FIELD;
            case PACKAGE -> SourceElementKind.PACKAGE;
            case MODULE -> SourceElementKind.MODULE;
            case LOCAL_VARIABLE, PARAMETER, RESOURCE_VARIABLE, EXCEPTION_PARAMETER, BINDING_VARIABLE,
                 TYPE_PARAMETER -> SourceElementKind.VARIABLE;
            default -> null;
        };
    }

    private static SourceTypeKind typeKind(ElementKind kind) {
        return switch (kind) {
            case CLASS -> SourceTypeKind.CLASS;
            case INTERFACE -> SourceTypeKind.INTERFACE;
            case ENUM -> SourceTypeKind.ENUM;
            case RECORD -> SourceTypeKind.RECORD;
            case ANNOTATION_TYPE -> SourceTypeKind.ANNOTATION;
            case CONSTRUCTOR -> SourceTypeKind.CONSTRUCTOR;
            default -> SourceTypeKind.OTHER;
        };
    }

    /** This module's markers from {@code icons/}, the tool bar glyphs from the shared {@link ToolIcons}. */
    private static Image load(String name) {
        URL local = UsagesIcons.class.getResource("icons/" + name + ".png");
        if (local != null) {
            return new Image(local.toExternalForm());
        }
        return Objects.requireNonNull(ToolIcons.image(name), () -> "Missing icon " + name);
    }
}

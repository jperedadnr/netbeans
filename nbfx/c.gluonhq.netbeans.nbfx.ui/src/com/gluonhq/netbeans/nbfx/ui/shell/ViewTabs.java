package com.gluonhq.netbeans.nbfx.ui.shell;

import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import com.gluonhq.netbeans.nbfx.windows.ViewRegistration;
import com.gluonhq.netbeans.nbfx.windows.ViewRegistry;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/** Builds the tab of a {@link ViewProvider} and maps its dock locations to the launcher's panes. */
public final class ViewTabs {

    private static final Logger LOG = Logger.getLogger(ViewTabs.class.getName());
    private static final double ICON_SIZE = 16;
    private static final Map<String, Image> ICONS = new HashMap<>();

    private ViewTabs() {
    }

    /**
     * Builds the tab of {@code view}: a draggable {@link Label} graphic (with the icon the view's
     * registration declares, if any), its content, its stable id, and {@code tooltip} on the label
     * (the close button carries its own).
     */
    public static Tab create(ViewProvider view, String tooltip) {
        Tab tab = new Tab();
        // A Label graphic (not tab text) is required so the tab can be dragged/detached.
        Label label = new Label(view.getTitle());
        Node icon = icon(view);
        if (icon != null) {
            label.setGraphic(icon);
        }
        tab.setGraphic(label);
        tab.setContent(view.getView());
        NbfxTabPane.setViewId(tab, view.getId());
        NbfxTabPane.installTabLabelTooltip(tab, tooltip);
        NbfxTabPane.installCloseButtonTooltip(tab,
                NbBundle.getMessage(ViewTabs.class, "Tab.view.close.tooltip"));
        TabContextMenu.installForView(tab);
        return tab;
    }

    /**
     * A new 16-pixel view of the icon declared for {@code view} with
     * {@code @FxViewRegistration(iconName = ...)}, a resource path resolved through the system class
     * loader as NetBeans resolves an {@code iconBase}; {@code null} for a view without one.
     */
    private static Node icon(ViewProvider view) {
        ViewRegistration registration = ViewRegistry.find(view.getId());
        String iconName = registration == null ? "" : registration.iconName();
        if (iconName.isEmpty()) {
            return null;
        }
        Image image = ICONS.computeIfAbsent(iconName, name -> {
            ClassLoader loader = Lookup.getDefault().lookup(ClassLoader.class);
            URL url = loader == null ? null : loader.getResource(name);
            if (url == null) {
                LOG.warning("Icon resource not found: " + name);
                return null;
            }
            return new Image(url.toExternalForm());
        });
        if (image == null) {
            return null;
        }
        ImageView glyph = new ImageView(image);
        glyph.setFitWidth(ICON_SIZE);
        glyph.setFitHeight(ICON_SIZE);
        glyph.setPreserveRatio(true);
        return glyph;
    }
}

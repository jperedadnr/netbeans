package com.gluonhq.netbeans.nbfx.properties;

import com.gluonhq.netbeans.nbfx.annotations.FxViewLocation;
import com.gluonhq.netbeans.nbfx.annotations.FxViewRegistration;
import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;
import com.gluonhq.netbeans.nbfx.api.view.DockLocation;
import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Properties view: a dockable tab on the right showing the {@link FxPropertySheet} for the file
 * selected in the navigator.
 */
@ServiceProvider(service = ViewProvider.class)
@FxViewRegistration(id = PropertiesView.ID, displayName = "Properties",
        location = FxViewLocation.RIGHT, position = 100)
public final class PropertiesView implements ViewProvider {

    /** The stable id of the view. */
    public static final String ID = "properties";

    private final FxPropertySheet sheet = new FxPropertySheet();
    private final ChangeListener<List<FileObject>> selectionListener =
            (observable, old, now) -> sheet.setContext(now == null || now.isEmpty() ? null : now.get(0));

    /** Creates the view. Must be called on the JavaFX Application Thread. */
    public PropertiesView() {
        FileSelectionContext selection = Lookup.getDefault().lookup(FileSelectionContext.class);
        if (selection != null) {
            selection.selectedFiles().addListener(selectionListener);
            List<FileObject> files = selection.selectedFiles().getValue();
            sheet.setContext(files == null || files.isEmpty() ? null : files.get(0));
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(PropertiesView.class, "PropertiesView.title");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.RIGHT;
    }

    @Override
    public Node getView() {
        return sheet;
    }

    /** Brings the Properties view on screen. Must run on the JavaFX Application Thread. */
    public void show() {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static PropertiesView instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(PropertiesView.class::isInstance)
                .map(PropertiesView.class::cast)
                .findFirst()
                .orElse(null);
    }
}

package com.gluonhq.netbeans.nbfx.properties;

import com.gluonhq.netbeans.nbfx.api.actions.FileContextMenuContributor;
import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;
import java.util.List;
import javafx.scene.control.MenuItem;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Adds "Properties" to a file's context menu, selecting the file and showing the {@link PropertiesView}.
 * Folders are left to the project customizer, so the two never add a duplicate item on a project root.
 */
@ServiceProvider(service = FileContextMenuContributor.class)
public final class PropertiesMenuContributor implements FileContextMenuContributor {

    @Override
    public List<MenuItem> itemsFor(FileObject file) {
        return List.of(item(file));
    }

    private static MenuItem item(FileObject file) {
        MenuItem item = new MenuItem(NbBundle.getMessage(PropertiesMenuContributor.class,
                "PropertiesMenuContributor.properties"));
        item.setOnAction(event -> {
            FileSelectionContext selection = Lookup.getDefault().lookup(FileSelectionContext.class);
            if (selection != null) {
                selection.setSelectedFiles(List.of(file));
            }
            PropertiesView view = PropertiesView.instance();
            if (view != null) {
                view.show();
            }
        });
        return item;
    }
}

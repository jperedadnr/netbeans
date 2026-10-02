package com.gluonhq.netbeans.nbfx.findusages.actions;

import com.gluonhq.netbeans.nbfx.api.actions.FileContextMenuContributor;
import com.gluonhq.netbeans.nbfx.findusages.model.UsagesModel;
import java.util.List;
import javafx.scene.control.MenuItem;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * "Find Usages" on a Java file node of the Projects / Files views: searches the usages of the
 * file's top-level type, as NetBeans does on a class node.
 */
@ServiceProvider(service = FileContextMenuContributor.class)
public final class FindUsagesMenuContributor implements FileContextMenuContributor {

    @Override
    public List<MenuItem> itemsFor(FileObject file) {
        if (file == null || !"java".equalsIgnoreCase(file.getExt())) {
            return List.of();
        }
        MenuItem item = new MenuItem(NbBundle.getMessage(FindUsagesMenuContributor.class, "CTL_FindUsagesCommand"));
        // Shown for consistency with the editor's item; the key itself is bound to the editor command.
        item.setAccelerator(FindUsagesCommand.SHORTCUT);
        item.setOnAction(e -> {
            UsagesModel model = Lookup.getDefault().lookup(UsagesModel.class);
            if (model != null) {
                FindUsagesCommand.show(model.findUsagesOfType(file),
                        NbBundle.getMessage(FindUsagesMenuContributor.class, "ERR_NoTypeInFile", file.getNameExt()));
            }
        });
        return List.of(item);
    }
}

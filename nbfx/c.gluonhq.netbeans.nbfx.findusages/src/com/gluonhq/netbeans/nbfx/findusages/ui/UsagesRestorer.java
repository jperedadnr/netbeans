package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.findusages.model.UsagesModel;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsagesSettings.SavedQuery;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.classpath.GlobalPathRegistryEvent;
import org.netbeans.api.java.classpath.GlobalPathRegistryListener;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Runs last session's queries again. A query is started once the source root of its file is
 * registered - the projects of the session open in the background after the window layout is
 * restored - so it searches the whole project, not only its own file; queries of files that are
 * gone or never come back into an open project are dropped.
 */
final class UsagesRestorer implements GlobalPathRegistryListener {

    private static final Logger LOG = Logger.getLogger(UsagesRestorer.class.getName());

    private final UsagesModel model;
    private final Deque<SavedQuery> pending;

    private UsagesRestorer(UsagesModel model, List<SavedQuery> saved) {
        this.model = model;
        this.pending = new ArrayDeque<>(saved);
    }

    static void restore(UsagesModel model, List<SavedQuery> saved) {
        if (saved.isEmpty()) {
            return;
        }
        UsagesRestorer restorer = new UsagesRestorer(model, saved);
        GlobalPathRegistry.getDefault().addGlobalPathRegistryListener(restorer);
        restorer.attempt();
    }

    @Override
    public void pathsAdded(GlobalPathRegistryEvent event) {
        attempt();
    }

    @Override
    public void pathsRemoved(GlobalPathRegistryEvent event) {
    }

    private synchronized void attempt() {
        var roots = GlobalPathRegistry.getDefault().getSourceRoots();
        for (Iterator<SavedQuery> it = pending.iterator(); it.hasNext();) {
            SavedQuery saved = it.next();
            FileObject file = FileUtil.toFileObject(new File(saved.path()));
            if (file == null) {
                LOG.log(Level.FINE, "Dropping the usages of a missing file: {0}", saved.path());
                it.remove();
            } else if (roots.stream().anyMatch(root -> FileUtil.isParentOf(root, file))) {
                // The sources are described on the JavaFX thread: the open-files scope asks the UI.
                Platform.runLater(() -> model.findUsages(file, saved.offset(), saved.options()));
                it.remove();
            }
        }
        if (pending.isEmpty()) {
            GlobalPathRegistry.getDefault().removeGlobalPathRegistryListener(this);
        }
    }
}

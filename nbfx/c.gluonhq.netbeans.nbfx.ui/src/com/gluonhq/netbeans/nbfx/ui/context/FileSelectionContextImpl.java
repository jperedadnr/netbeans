package com.gluonhq.netbeans.nbfx.ui.context;

import com.gluonhq.netbeans.nbfx.ui.JavaFXLaunchApp;

import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;

import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 * Default {@link FileSelectionContext} implementation, holding the navigator selection and the
 * navigator-focused state as observable JavaFX state. It is updated by {@link JavaFXLaunchApp}
 * (which owns the navigator views and the focus tracking) and observed by the file action
 * providers.
 */
@ServiceProvider(service = FileSelectionContext.class)
public class FileSelectionContextImpl implements FileSelectionContext {

    private final ObjectProperty<List<FileObject>> selectedFiles =
            new SimpleObjectProperty<>(this, "selectedFiles", List.of());
    private final BooleanProperty navigatorFocused =
            new SimpleBooleanProperty(this, "navigatorFocused");

    @Override
    public ObservableValue<List<FileObject>> selectedFiles() {
        return selectedFiles;
    }

    @Override
    public void setSelectedFiles(List<FileObject> files) {
        selectedFiles.set(files == null ? List.of() : files);
    }

    @Override
    public ObservableValue<Boolean> navigatorFocused() {
        return navigatorFocused;
    }

    @Override
    public void setNavigatorFocused(boolean focused) {
        navigatorFocused.set(focused);
    }
}

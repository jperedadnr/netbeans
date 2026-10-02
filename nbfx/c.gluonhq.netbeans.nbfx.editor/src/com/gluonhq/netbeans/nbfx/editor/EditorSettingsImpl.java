package com.gluonhq.netbeans.nbfx.editor;

import com.gluonhq.netbeans.nbfx.api.editor.EditorSettings;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import org.openide.util.lookup.ServiceProvider;

/**
 * Default {@link EditorSettings} holding the shared, observable display preferences. Line numbers
 * and breadcrumbs are shown by default; the launcher overrides this from persisted state at startup.
 */
@ServiceProvider(service = EditorSettings.class)
public class EditorSettingsImpl implements EditorSettings {

    private final BooleanProperty showLineNumbers = new SimpleBooleanProperty(this, "showLineNumbers", true);
    private final BooleanProperty showBreadcrumbs = new SimpleBooleanProperty(this, "showBreadcrumbs", true);

    @Override
    public BooleanProperty showLineNumbers() {
        return showLineNumbers;
    }

    @Override
    public BooleanProperty showBreadcrumbs() {
        return showBreadcrumbs;
    }
}

package com.gluonhq.netbeans.nbfx.properties;

import javafx.scene.Node;

/**
 * Creates the JavaFX editor for a {@link FxProperty} of a given value type. Registered via
 * {@code @ServiceProvider}; the first editor whose {@link #valueType() type} can hold the property's
 * value type wins.
 */
public interface FxPropertyEditor {

    /** The value type this editor handles (e.g. {@code String.class}). */
    Class<?> valueType();

    /** Builds the editor for {@code property}. Called on the JavaFX thread. */
    Node createEditor(FxProperty property);
}

package com.gluonhq.netbeans.nbfx.properties;

import javafx.scene.Node;
import javafx.scene.control.Label;
import org.openide.util.Lookup;

/**
 * Resolves the {@link FxPropertyEditor} for a property: the first registered editor whose value type
 * can hold the property's value type, or a read-only label when none matches.
 */
public final class FxPropertyEditors {

    private FxPropertyEditors() {
    }

    public static Node createEditor(FxProperty property) {
        if (property.isWritable()) {
            for (FxPropertyEditor editor : Lookup.getDefault().lookupAll(FxPropertyEditor.class)) {
                if (editor.valueType().isAssignableFrom(property.getValueType())) {
                    return editor.createEditor(property);
                }
            }
        }
        return new Label(display(property.getValue()));
    }

    static String display(Object value) {
        return value == null ? "" : value.toString();
    }
}

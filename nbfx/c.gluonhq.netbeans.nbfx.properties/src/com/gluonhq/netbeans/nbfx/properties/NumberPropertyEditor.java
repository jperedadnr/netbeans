package com.gluonhq.netbeans.nbfx.properties;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.openide.util.lookup.ServiceProvider;

/** The editor for {@link Number} values (integer and floating point). */
@ServiceProvider(service = FxPropertyEditor.class)
public final class NumberPropertyEditor implements FxPropertyEditor {

    @Override
    public Class<?> valueType() {
        return Number.class;
    }

    @Override
    public Node createEditor(FxProperty property) {
        TextField field = new TextField(FxPropertyEditors.display(property.getValue()));
        field.setEditable(property.isWritable());
        field.textProperty().addListener((observable, old, now) -> {
            if (property.isWritable()) {
                property.setValue(parse(now, property.getValueType(), property.getValue()));
            }
        });
        return field;
    }

    private static Object parse(String text, Class<?> type, Object fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            if (type == Integer.class || type == int.class) {
                return Integer.valueOf(text.trim());
            }
            if (type == Long.class || type == long.class) {
                return Long.valueOf(text.trim());
            }
            if (type == Double.class || type == double.class) {
                return Double.valueOf(text.trim());
            }
            if (type == Float.class || type == float.class) {
                return Float.valueOf(text.trim());
            }
            return Double.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}

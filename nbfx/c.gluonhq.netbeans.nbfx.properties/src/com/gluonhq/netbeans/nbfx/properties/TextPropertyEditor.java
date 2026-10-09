package com.gluonhq.netbeans.nbfx.properties;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.openide.util.lookup.ServiceProvider;

/** The editor for {@link String} values. */
@ServiceProvider(service = FxPropertyEditor.class)
public final class TextPropertyEditor implements FxPropertyEditor {

    @Override
    public Class<?> valueType() {
        return String.class;
    }

    @Override
    public Node createEditor(FxProperty property) {
        TextField field = new TextField(FxPropertyEditors.display(property.getValue()));
        field.setEditable(property.isWritable());
        field.textProperty().addListener((observable, old, now) -> {
            if (property.isWritable()) {
                property.setValue(now);
            }
        });
        return field;
    }
}

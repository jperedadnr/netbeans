package com.gluonhq.netbeans.nbfx.properties;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import org.openide.util.lookup.ServiceProvider;

/** The editor for {@link Boolean} values. */
@ServiceProvider(service = FxPropertyEditor.class)
public final class BooleanPropertyEditor implements FxPropertyEditor {

    @Override
    public Class<?> valueType() {
        return Boolean.class;
    }

    @Override
    public Node createEditor(FxProperty property) {
        CheckBox box = new CheckBox();
        box.setSelected(Boolean.TRUE.equals(property.getValue()));
        box.setDisable(!property.isWritable());
        box.selectedProperty().addListener((observable, old, now) -> {
            if (property.isWritable()) {
                property.setValue(now);
            }
        });
        return box;
    }
}

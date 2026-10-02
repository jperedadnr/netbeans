package com.gluonhq.netbeans.nbfx.editor.codearea;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.stage.Window;
import jfx.incubator.scene.control.richtext.CodeArea;

/**
 * Moves keyboard focus to a {@link CodeArea} as soon as it is ready for it: waits, in turn, for
 * its skin, its scene, its window, and finally for that window to be focused.
 */
final class EditorFocusSupport {

    private EditorFocusSupport() {
    }

    /** Focuses the code area (so its caret starts blinking) once it is ready. */
    static void requestWhenReady(CodeArea codeArea) {
        if (codeArea.getSkin() == null) {
            onceReady(codeArea, codeArea.skinProperty());
            return;
        }
        Scene scene = codeArea.getScene();
        if (scene == null) {
            onceReady(codeArea, codeArea.sceneProperty());
            return;
        }
        Window window = scene.getWindow();
        if (window == null) {
            onceReady(codeArea, scene.windowProperty());
            return;
        }
        if (!window.isFocused()) {
            // Focus the editor as soon as its window gains focus.
            window.focusedProperty().addListener(new ChangeListener<>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> obs, Boolean ov, Boolean focused) {
                    if (Boolean.TRUE.equals(focused)) {
                        window.focusedProperty().removeListener(this);
                        Platform.runLater(codeArea::requestFocus);
                    }
                }
            });
            return;
        }
        Platform.runLater(codeArea::requestFocus);
    }

    /** Re-runs {@link #requestWhenReady(CodeArea)} once {@code property} becomes non-null. */
    private static void onceReady(CodeArea codeArea, ObservableValue<?> property) {
        property.addListener(new InvalidationListener() {
            @Override
            public void invalidated(Observable observable) {
                if (property.getValue() != null) {
                    property.removeListener(this);
                    Platform.runLater(() -> requestWhenReady(codeArea));
                }
            }
        });
    }
}

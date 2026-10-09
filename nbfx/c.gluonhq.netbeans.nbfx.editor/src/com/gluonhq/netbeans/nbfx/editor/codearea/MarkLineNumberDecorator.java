package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.api.editor.EditorAnnotation;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import jfx.incubator.scene.control.richtext.Marker;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;

import java.text.DecimalFormat;
import java.util.Arrays;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.SideDecorator;

/**
 * A {@link SideDecorator} that shows line numbers (like the built-in
 * {@code LineNumberDecorator}) but also decorates lines that have
 * error or warning diagnostics with a mark and a tooltip, and shows the
 * {@link EditorAnnotation} glyph of a line in place of its number, as NetBeans does.
 */
public class MarkLineNumberDecorator implements SideDecorator {

    private static final DecimalFormat FORMAT = new DecimalFormat("###0");

    /** Gutter width kept when line numbers are hidden, so error/warning indicators still have room. */
    private static final double INDICATOR_ONLY_WIDTH = 10;
    /** The side of the annotation glyphs. */
    private static final double BADGE_SIZE = 16;

    private final BaseSyntaxDecorator syntaxDecorator;
    private final ObjectProperty<Font> fontProperty;

    private boolean showLineNumbers = true;
    /** The annotations, on markers so they follow the text's edits. */
    private final List<PlacedAnnotation> annotations = new ArrayList<>();

    private record PlacedAnnotation(Marker marker, EditorAnnotation annotation) {
    }

    /**
     * @param syntaxDecorator the decorator that tracks error/warning diagnostics
     * @param fontProperty    the CodeArea font property to bind label fonts to
     */
    public MarkLineNumberDecorator(BaseSyntaxDecorator syntaxDecorator, ObjectProperty<Font> fontProperty) {
        this.syntaxDecorator = syntaxDecorator;
        this.fontProperty = fontProperty;
    }

    /** Sets whether line numbers are shown.*/
    public void setShowLineNumbers(boolean showLineNumbers) {
        this.showLineNumbers = showLineNumbers;
    }

    /** Replaces the annotations shown, each kept on a marker of {@code model} at its line. */
    public void setAnnotations(CodeTextModel model, List<EditorAnnotation> newAnnotations) {
        annotations.clear();
        for (EditorAnnotation annotation : newAnnotations) {
            if (annotation.line() < model.size()) {
                annotations.add(new PlacedAnnotation(model.getMarker(TextPos.ofLeading(annotation.line(), 0)), annotation));
            }
        }
    }

    private EditorAnnotation annotationAt(int index) {
        for (PlacedAnnotation placed : annotations) {
            if (placed.marker().getIndex() == index) {
                return placed.annotation();
            }
        }
        return null;
    }

    @Override
    public double getPrefWidth(double viewWidth) {
        return 0;
    }

    @Override
    public Node getMeasurementNode(int index) {
        if (!showLineNumbers && !annotations.isEmpty()) {
            // No numbers to take the glyphs' place: room for a glyph after the diagnostic dot.
            Region spacer = new Region();
            spacer.setMinSize(INDICATOR_ONLY_WIDTH + BADGE_SIZE, 1);
            spacer.setPrefSize(INDICATOR_ONLY_WIDTH + BADGE_SIZE, 1);
            return spacer;
        }
        String s = FORMAT.format(index + 300);
        char[] cs = new char[s.length()];
        Arrays.fill(cs, '8');
        return createNode(new String(cs), -1, null);
    }

    @Override
    public Node getNode(int index) {
        String severity = syntaxDecorator.getErrorSeverityOnLine(index);
        return createNode(FORMAT.format(index + 1), index, severity);
    }

    private Node createNode(String text, int index, String severity) {
        EditorAnnotation annotation = index < 0 ? null : annotationAt(index);
        if (severity == null && !showLineNumbers && annotation == null) {
            Region spacer = new Region();
            spacer.getStyleClass().add("line-number-decorator");
            spacer.setMinSize(INDICATOR_ONLY_WIDTH, 1);
            spacer.setPrefSize(INDICATOR_ONLY_WIDTH, 1);
            return spacer;
        }

        // Error/warning indicator: a small colored dot
        Color color = "error".equals(severity) ? Color.RED : Color.ORANGE;
        Circle indicator = new Circle(4, color);
        indicator.setManaged(false);
        indicator.setVisible(severity != null);

        HBox container = new HBox(2, indicator) {
            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                indicator.relocate(2, (getHeight() - indicator.getRadius() * 2) / 2);
            }
        };
        container.getStyleClass().add("line-number-decorator");
        container.setAlignment(Pos.CENTER_RIGHT);
        container.setMinSize(INDICATOR_ONLY_WIDTH, 1);
        container.setPrefSize(INDICATOR_ONLY_WIDTH, 1);
        container.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        if (annotation != null) {
            // The line's glyph takes the place of its number, at the right, as in NetBeans.
            StackPane badge = new StackPane();
            badge.getStyleClass().add("annotation-badge");
            badge.setMinSize(BADGE_SIZE, BADGE_SIZE);
            badge.setPrefSize(BADGE_SIZE, BADGE_SIZE);
            badge.setMaxSize(BADGE_SIZE, BADGE_SIZE);
            ImageView glyph = new ImageView(annotation.icon());
            glyph.setFitWidth(BADGE_SIZE);
            glyph.setFitHeight(BADGE_SIZE);
            glyph.setPreserveRatio(true);
            badge.getChildren().add(glyph);
            if (annotation.tooltip() != null) {
                Tooltip.install(badge, new Tooltip(annotation.tooltip()));
            }
            if (annotation.action() != null) {
                badge.setCursor(Cursor.HAND);
                badge.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        Bounds bounds = badge.localToScreen(badge.getBoundsInLocal());
                        annotation.action().accept(bounds == null
                                ? new Point2D(e.getScreenX(), e.getScreenY())
                                : new Point2D(bounds.getMaxX(), bounds.getMaxY()));
                        e.consume();
                    }
                });
            }
            container.getChildren().add(badge);
            container.setPrefWidth(-1);
        } else if (showLineNumbers) {
            Label numberLabel = new Label(text);
            numberLabel.getStyleClass().add("line-number-decorator-label");
            numberLabel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            numberLabel.setMinHeight(1);
            numberLabel.setPrefHeight(1);
            numberLabel.setAlignment(Pos.CENTER_RIGHT);
            numberLabel.setOpacity(1.0);
            if (fontProperty != null) {
                numberLabel.fontProperty().bind(fontProperty);
            }
            HBox.setHgrow(numberLabel, Priority.ALWAYS);
            container.getChildren().add(numberLabel);
            container.setPrefWidth(-1);
        }

        // Tooltip with the diagnostic message(s)
        String messages = syntaxDecorator.getErrorMessagesForLine(index);
        if (messages != null) {
            Tooltip tooltip = new Tooltip(messages);
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(500);
            Tooltip.install(container, tooltip);
        }

        return container;
    }
}

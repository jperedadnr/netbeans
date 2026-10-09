/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.api.editor.HyperlinkProvider;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * The editor's hyperlinks, as NetBeans' Shortcut+Click: while the shortcut key is held, the
 * identifier under the mouse shows as a link (underlined, in the link colour, with a hand cursor),
 * and a click follows it through the {@link HyperlinkProvider} registered for the file's kind (Go
 * to Declaration, for Java; with Alt held too, the alternative link: Go to Implementation). The cue is recomputed when the mouse moves and when the key goes
 * down or up, as pressing the key alone moves no mouse. Nothing shows for a file no provider
 * handles.
 */
final class HyperlinkSupport {

    private final CodeArea codeArea;
    private final FileObject file;
    private final CodeTextModel model;
    private final BaseSyntaxDecorator decorator;
    /** Where the mouse last was over the editor, in screen coordinates; {@code null} once it left. */
    private Point2D mouse;
    /** The node showing the hand cursor, and the cursor it had, to restore; {@code null} while the hand is not shown. */
    private Node cursorNode;
    private Cursor replaced;
    /** Set while a hyperlink press was taken, so its release is kept from the behaviour too. */
    private boolean following;

    private HyperlinkSupport(CodeArea codeArea, FileObject file, CodeTextModel model, BaseSyntaxDecorator decorator) {
        this.codeArea = codeArea;
        this.file = file;
        this.model = model;
        this.decorator = decorator;
    }

    static void install(CodeArea codeArea, FileObject file, CodeTextModel model, BaseSyntaxDecorator decorator) {
        HyperlinkSupport support = new HyperlinkSupport(codeArea, file, model, decorator);
        codeArea.addEventFilter(MouseEvent.MOUSE_PRESSED, support::onPressed);
        codeArea.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (support.following) {
                support.following = false;
                e.consume();
            }
        });
        codeArea.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            support.mouse = new Point2D(e.getScreenX(), e.getScreenY());
            support.update(e.isShortcutDown());
        });
        codeArea.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> support.update(false));
        codeArea.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            support.mouse = null;
            support.update(false);
        });
        // The modifier alone: shown as soon as it goes down, gone as soon as it goes up.
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> support.update(e.isShortcutDown()));
        codeArea.addEventFilter(KeyEvent.KEY_RELEASED, e -> support.update(e.isShortcutDown()));
        codeArea.focusedProperty().subscribe(focused -> {
            if (!focused) {
                support.update(false);
            }
        });
    }

    private void onPressed(MouseEvent e) {
        if (!isHyperlinkClick(e)) {
            return;
        }
        HyperlinkProvider provider = providerFor(file);
        TextPos position = provider == null ? null : codeArea.getTextPosition(e.getScreenX(), e.getScreenY());
        if (position == null || spanAt(provider, position) == null) {
            return;
        }
        e.consume();
        following = true;
        update(false);
        provider.open(file, TextOffsets.offsetOf(model, position), e.isAltDown(), new Point2D(e.getScreenX(), e.getScreenY()));
    }

    /** Shows the link under the mouse while {@code shortcutDown}, else none. */
    private void update(boolean shortcutDown) {
        TextPos position = shortcutDown && mouse != null ? codeArea.getTextPosition(mouse.getX(), mouse.getY()) : null;
        HyperlinkProvider provider = position == null ? null : providerFor(file);
        int[] span = provider == null ? null : spanAt(provider, position);
        if (span == null) {
            decorator.clearHyperlink(model);
            if (cursorNode != null) {
                cursorNode.setCursor(replaced);
                cursorNode = null;
                replaced = null;
            }
            return;
        }
        decorator.setHyperlink(model, TextPos.ofLeading(position.index(), span[0]), TextPos.ofLeading(position.index(), span[1]));
        if (cursorNode == null) {
            // The skin's content pane has its own (text) cursor, which the control's would not override.
            Node content = codeArea.lookup(".vflow .content");
            cursorNode = content != null ? content : codeArea;
            replaced = cursorNode.getCursor();
            cursorNode.setCursor(Cursor.HAND);
        }
    }

    /** The link's span at {@code position}, by the provider, given what the lexer makes of the text there. */
    private int[] spanAt(HyperlinkProvider provider, TextPos position) {
        String line = model.getPlainText(position.index());
        if (line == null) {
            return null;
        }
        int column = position.charIndex();
        return provider.hyperlinkSpan(line, column, decorator.textKindAt(position.index(), column));
    }

    private static boolean isHyperlinkClick(MouseEvent e) {
        return e.getButton() == MouseButton.PRIMARY && e.isShortcutDown() && e.getClickCount() == 1
                && !e.isPopupTrigger() && !e.isShiftDown();
    }

    /** The first registered provider handling {@code file}, or {@code null}. */
    private static HyperlinkProvider providerFor(FileObject file) {
        for (HyperlinkProvider provider : Lookup.getDefault().lookupAll(HyperlinkProvider.class)) {
            if (provider.handles(file)) {
                return provider;
            }
        }
        return null;
    }
}

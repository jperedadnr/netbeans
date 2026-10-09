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
package com.gluonhq.netbeans.nbfx.javanavigator.actions;

import com.gluonhq.netbeans.nbfx.api.editor.HyperlinkProvider;
import com.gluonhq.netbeans.nbfx.javanavigator.model.GoToResolver;
import com.gluonhq.netbeans.nbfx.javanavigator.ui.GoToOpener;
import java.util.concurrent.CompletableFuture;
import javafx.geometry.Point2D;
import java.util.regex.Pattern;
import javax.lang.model.SourceVersion;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 * Shortcut+Click in a Java editor: Go to Declaration at the clicked identifier, quietly when
 * there is nothing to go to there; with Alt held too, Go to Implementation. Identifiers show as links while the key is held - in code, and in
 * Javadoc references - keywords and literals not.
 */
@ServiceProvider(service = HyperlinkProvider.class)
public class GoToHyperlinkProvider implements HyperlinkProvider {

    /** What precedes an identifier of a Javadoc reference on its line: an open inline tag, or a block tag's name. */
    private static final Pattern JAVADOC_REFERENCE = Pattern.compile(
            "(\\{@(link|linkplain|value)\\s+[^}]*|@(see|param|throws|exception)\\s+<?[\\w.#(),]*)$");

    @Override
    public boolean handles(FileObject file) {
        return file != null && "java".equalsIgnoreCase(file.getExt());
    }

    /**
     * In code, the identifier at the column unless it is a keyword other than {@code this} /
     * {@code super}; in a comment, an identifier that is part of a Javadoc reference - of a
     * {@code {@link}}, {@code {@linkplain}} or {@code {@value}} tag, or the name after
     * {@code @see}, {@code @param}, {@code @throws} or {@code @exception}; nothing in a literal.
     */
    @Override
    public int[] hyperlinkSpan(String line, int column, TextKind kind) {
        int[] span = kind == TextKind.LITERAL ? null : HyperlinkProvider.identifierAt(line, column);
        if (span == null) {
            return null;
        }
        if (kind == TextKind.COMMENT) {
            return JAVADOC_REFERENCE.matcher(line.substring(0, span[0])).find() ? span : null;
        }
        String word = line.substring(span[0], span[1]);
        return SourceVersion.isKeyword(word) && !"this".equals(word) && !"super".equals(word) ? null : span;
    }

    @Override
    public CompletableFuture<Boolean> open(FileObject file, int offset, boolean alternative, Point2D anchor) {
        return alternative
                ? GoToOpener.goToImplementation(file, offset, anchor, null)
                : GoToOpener.goTo(GoToResolver.Kind.DECLARATION, file, offset, null);
    }
}

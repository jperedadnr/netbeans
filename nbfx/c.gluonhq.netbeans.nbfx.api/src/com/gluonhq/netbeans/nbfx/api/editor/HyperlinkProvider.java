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
package com.gluonhq.netbeans.nbfx.api.editor;

import java.util.concurrent.CompletableFuture;
import javafx.geometry.Point2D;
import org.openide.filesystems.FileObject;

/**
 * Follows a hyperlink in an editor - the Shortcut+Click of NetBeans' hyperlink providers, which
 * goes to the declaration of the element clicked, and the Alt+Shortcut+Click alternative. Implementations are registered in the global
 * {@link org.openide.util.Lookup} by the modules that can resolve a kind of file (the Java
 * navigator for {@code .java} files); the editor asks the first one that {@link #handles} the file.
 */
public interface HyperlinkProvider {

    /** Whether this provider follows the hyperlinks of {@code file}. */
    boolean handles(FileObject file);

    /** What the lexer makes of the text at a point: code, a comment, or a string / character literal. */
    enum TextKind {
        CODE, COMMENT, LITERAL
    }

    /**
     * The span of the hyperlink at {@code column} of {@code line}, a line of a file this provider
     * handles, as {@code {start, end}} with {@code end} exclusive; {@code null} when there is none
     * there. The editor shows the span as a link while the shortcut key is held over it. The
     * default is the identifier around the column in code, and nothing in a comment or a literal;
     * a provider refines it (leaving out the keywords of its language, linking the references of
     * its documentation comments, say) without compiling, as this is asked on every mouse move.
     *
     * @param line   the text of the line
     * @param column the column in it
     * @param kind   what the lexer makes of the text at the column
     */
    default int[] hyperlinkSpan(String line, int column, TextKind kind) {
        return kind == TextKind.CODE ? identifierAt(line, column) : null;
    }

    /** The span of the identifier around {@code column} of {@code line}, or {@code null}. */
    static int[] identifierAt(String line, int column) {
        int start = Math.clamp(column, 0, line.length());
        int end = start;
        while (start > 0 && Character.isJavaIdentifierPart(line.charAt(start - 1))) {
            start--;
        }
        while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
            end++;
        }
        return start < end && Character.isJavaIdentifierStart(line.charAt(start)) ? new int[] {start, end} : null;
    }

    /**
     * Opens what the text at {@code offset} of {@code file} refers to, taking the editor's current
     * text into account. Called on the FX thread; completes with {@code true} when a target was
     * opened and {@code false} when there is nothing to follow there, which the editor ignores -
     * a click on a keyword or on whitespace is not an error.
     *
     * @param file        the file open in the editor
     * @param offset      the offset clicked, counting one character per line break
     * @param alternative whether the Alt key was held too: NetBeans' alternative hyperlink, which
     *                    for Java lists the implementations instead of going to the declaration
     * @param anchor      where the click was, in screen coordinates, for a popup the provider shows
     * @return whether a target was opened
     */
    CompletableFuture<Boolean> open(FileObject file, int offset, boolean alternative, Point2D anchor);
}

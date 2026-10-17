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
package com.gluonhq.netbeans.nbfx.structure.maven;

import com.gluonhq.netbeans.nbfx.structure.xml.XmlNode;
import com.gluonhq.netbeans.nbfx.structure.xml.XmlScanner;
import java.util.HashMap;
import java.util.Map;
import org.apache.maven.model.InputLocation;

/**
 * Finds the XML elements of one POM's text that the rows of the POM model tree stand for: by the
 * position the Maven reader recorded for the element (right after its start tag), else - for the
 * container elements it records none for - as the child of the parent row's element.
 */
final class PomLocator {

    private final XmlNode document;
    private final int[] lineStarts;

    /** Scans {@code text}, the POM of one layer. */
    PomLocator(String text) {
        document = XmlScanner.scan(text, () -> false);
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        lineStarts = new int[lines];
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lineStarts[line++] = i + 1;
            }
        }
    }

    /** The document scanned. */
    XmlNode document() {
        return document;
    }

    /** The element standing for {@code node} in layer {@code layer}, or {@code null} when there is none. */
    XmlNode locate(PomNode node, int layer) {
        if (node.parent() == null) {
            return document.child("project");
        }
        InputLocation location = node.locationAt(layer);
        if (location != null) {
            XmlNode element = elementAt(location);
            if (element != null) {
                return element;
            }
        }
        if (node.isListItem() || !node.definedIn(layer)) {
            return null;
        }
        XmlNode parentElement = locate(node.parent(), layer);
        return parentElement == null ? null : parentElement.child(node.elementName());
    }

    /** The elements of layer {@code layer} by the rows standing for them, the outermost row for an element shared. */
    Map<XmlNode, PomNode> map(PomNode root, int layer) {
        Map<XmlNode, PomNode> elements = new HashMap<>();
        map(root, layer, elements);
        return elements;
    }

    private void map(PomNode node, int layer, Map<XmlNode, PomNode> elements) {
        if (!node.definedIn(layer)) {
            return;
        }
        XmlNode element = locate(node, layer);
        if (element != null) {
            elements.putIfAbsent(element, node);
        }
        for (PomNode child : node.children()) {
            map(child, layer, elements);
        }
    }

    private XmlNode elementAt(InputLocation location) {
        int line = location.getLineNumber();
        int column = location.getColumnNumber();
        if (line < 1 || line > lineStarts.length || column < 1) {
            return null;
        }
        // The reader's position is right after the start tag's '>': the character before it is the
        // tag's, which no child element holds.
        int offset = lineStarts[line - 1] + column - 2;
        XmlNode node = offset < 0 ? null : document.nodeAt(offset);
        return node != null && node.kind() == XmlNode.Kind.ELEMENT ? node : null;
    }
}

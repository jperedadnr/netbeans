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
package com.gluonhq.netbeans.nbfx.structure.xml;

import com.gluonhq.netbeans.nbfx.structure.xml.XmlNode.Kind;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Checks what {@link XmlScanner} reads from well-formed and from broken XML texts. */
public class XmlScannerTest {

    private static XmlNode scan(String text) {
        return XmlScanner.scan(text, () -> false);
    }

    @Test
    public void readsElementsAttributesAndContent() {
        String text = "<?xml version=\"1.0\"?>\n<a x=\"1\" y='two'>\n  <b>text</b>\n  <c/>\n</a>\n";
        XmlNode document = scan(text);
        assertEquals(Kind.DOCUMENT, document.kind());
        List<XmlNode> top = document.children();
        assertEquals(2, top.size());

        XmlNode pi = top.get(0);
        assertEquals(Kind.PI, pi.kind());
        assertEquals("xml", pi.name());
        assertEquals("version=\"1.0\"", pi.text());
        assertEquals(0, pi.start());
        assertEquals(text.indexOf("?>") + 2, pi.end());

        XmlNode a = top.get(1);
        assertEquals(Kind.ELEMENT, a.kind());
        assertEquals("a", a.name());
        assertEquals(List.of("x", "y"), List.copyOf(a.attributes().keySet()));
        assertEquals("1", a.attributes().get("x"));
        assertEquals("two", a.attributes().get("y"));
        assertEquals(text.indexOf("<a"), a.start());
        assertEquals(text.indexOf("'two'>") + "'two'>".length(), a.headEnd());
        assertEquals(text.indexOf("</a>") + 4, a.end());
        assertEquals("", a.text());
        assertFalse(a.hasError());
        assertEquals(0, a.descendantErrors());

        assertEquals(2, a.children().size());
        XmlNode b = a.children().get(0);
        assertEquals("b", b.name());
        assertEquals("text", b.text());
        assertEquals(text.indexOf("<b>"), b.start());
        assertEquals(text.indexOf("</b>") + 4, b.end());
        XmlNode c = a.children().get(1);
        assertEquals("c", c.name());
        assertEquals(text.indexOf("<c/>"), c.start());
        assertEquals(text.indexOf("<c/>") + 4, c.end());
        assertEquals(c.end(), c.headEnd());
        assertTrue(c.children().isEmpty());

        assertSame(b, document.nodeAt(text.indexOf("text")));
        assertSame(c, document.nodeAt(text.indexOf("<c/>") + 1));
        assertSame(a, document.nodeAt(text.indexOf("</a>")));
        assertNull(document.nodeAt(text.length() - 1));
        assertSame(b, a.child("b"));
        assertNull(a.child("z"));
    }

    @Test
    public void keepsEntityReferencesAsWritten() {
        XmlNode document = scan("<a t=\"x&amp;y\">p &lt; q</a>");
        XmlNode a = document.children().get(0);
        assertEquals("x&amp;y", a.attributes().get("t"));
        assertEquals("p &lt; q", a.text());
    }

    @Test
    public void readsDoctypeCdataAndSkipsComments() {
        String text = "<!DOCTYPE html PUBLIC \"-//W3C\" \"x.dtd\">\n<html><!-- a comment --><![CDATA[ raw < data ]]><b/></html>";
        XmlNode document = scan(text);
        assertEquals(2, document.children().size());
        XmlNode doctype = document.children().get(0);
        assertEquals(Kind.DOCTYPE, doctype.kind());
        assertEquals("html PUBLIC \"-//W3C\" \"x.dtd\"", doctype.text().trim());
        assertEquals(0, doctype.start());
        assertEquals(text.indexOf(">") + 1, doctype.end());
        XmlNode html = document.children().get(1);
        assertEquals(2, html.children().size());
        XmlNode cdata = html.children().get(0);
        assertEquals(Kind.CDATA, cdata.kind());
        assertEquals("raw < data", cdata.text());
        assertEquals(text.indexOf("<![CDATA["), cdata.start());
        assertEquals(text.indexOf("]]>") + 3, cdata.end());
        assertEquals("b", html.children().get(1).name());
        assertEquals("", html.text());
    }

    @Test
    public void marksUnclosedElements() {
        String text = "<a>\n  <b>\n  <c/>\n</a>\n";
        XmlNode document = scan(text);
        XmlNode a = document.children().get(0);
        assertFalse(a.hasError());
        assertEquals(1, a.descendantErrors());
        XmlNode b = a.children().get(0);
        assertTrue(b.hasError());
        assertEquals("c", b.children().get(0).name());
        // b is cut short where a's end tag closes it, a ends with its end tag.
        assertEquals(text.indexOf("</a>"), b.end());
        assertEquals(text.indexOf("</a>") + 4, a.end());

        XmlNode unterminated = scan("<a><b></b>").children().get(0);
        assertTrue(unterminated.hasError());
        assertEquals(10, unterminated.end());
        assertFalse(unterminated.children().get(0).hasError());
    }

    @Test
    public void marksStrayEndTags() {
        XmlNode a = scan("<a></b></a>").children().get(0);
        assertTrue(a.hasError());
        assertEquals(11, a.end());
    }

    @Test
    public void pathKeysTellSiblingsApart() {
        XmlNode root = scan("<r><x/><y/><x/></r>").children().get(0);
        assertEquals("ELEMENT:r#0", root.pathKey());
        assertEquals("ELEMENT:r#0/ELEMENT:x#0", root.children().get(0).pathKey());
        assertEquals("ELEMENT:r#0/ELEMENT:y#0", root.children().get(1).pathKey());
        assertEquals("ELEMENT:r#0/ELEMENT:x#1", root.children().get(2).pathKey());
    }

    @Test
    public void returnsNullWhenCancelled() {
        StringBuilder text = new StringBuilder("<r>");
        for (int i = 0; i < 1000; i++) {
            text.append("<e/>");
        }
        text.append("</r>");
        assertNull(XmlScanner.scan(text.toString(), () -> true));
    }
}

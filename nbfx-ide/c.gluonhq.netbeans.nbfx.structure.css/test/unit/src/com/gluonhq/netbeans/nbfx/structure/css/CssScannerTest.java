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
package com.gluonhq.netbeans.nbfx.structure.css;

import com.gluonhq.netbeans.nbfx.structure.css.CssScanner.Item;
import com.gluonhq.netbeans.nbfx.structure.css.CssScanner.Structure;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Checks what {@link CssScanner} reads from style sheets, well-formed and being edited. */
public class CssScannerTest {

    private static Structure scan(String text) {
        return CssScanner.scan(text, () -> false);
    }

    private static List<String> names(List<Item> items) {
        return items.stream().map(Item::name).toList();
    }

    @Test
    public void readsRulesClassesIdsAndElements() {
        String text = """
                /* a { comment } */
                .scroll-bar > .thumb,
                #main Text {
                    -fx-fill: red; /* } */
                    -fx-font: "a { b";
                }
                .corner:hover { }
                """;
        Structure structure = scan(text);
        assertEquals(List.of(".scroll-bar > .thumb, #main Text", ".corner:hover"), names(structure.rules()));
        Item first = structure.rules().get(0);
        assertEquals(text.indexOf(".scroll-bar"), first.start());
        assertEquals(text.indexOf("}", text.indexOf("\"a { b\"")) + 1, first.end());
        assertEquals(List.of(".scroll-bar", ".thumb", ".corner"), names(structure.classes()));
        assertEquals(List.of("#main"), names(structure.ids()));
        assertEquals(List.of("Text"), names(structure.elements()));
        assertTrue(structure.atRules().isEmpty());
        assertTrue(structure.imports().isEmpty());
        assertEquals(text.indexOf(".thumb"), structure.classes().get(1).start());
        assertSame(first, structure.ruleAt(text.indexOf("-fx-fill")));
        assertNull(structure.ruleAt(0));
    }

    @Test
    public void readsAtRulesAndImports() {
        String text = """
                @charset "UTF-8";
                @import url("base.css");
                @import 'other.css' screen;
                @media (max-width: 600px) {
                    .a { color: red; }
                }
                @font-face { font-family: X; }
                @keyframes spin { from { x: 0 } to { x: 1 } }
                """;
        Structure structure = scan(text);
        assertEquals(List.of("base.css", "other.css"), names(structure.imports()));
        assertEquals(List.of("@charset \"UTF-8\"", "@media (max-width: 600px)", "@font-face", "@keyframes spin"),
                names(structure.atRules()));
        assertEquals(List.of(".a"), names(structure.rules()));
        assertEquals(List.of(".a"), names(structure.classes()));
        assertEquals(text.indexOf("@media"), structure.atRules().get(1).start());
        assertEquals(text.indexOf("}", text.indexOf("color: red; }") + 13) + 1, structure.atRules().get(1).end());
    }

    @Test
    public void skipsAttributeSelectorsAndPseudoClasses() {
        Structure structure = scan("a[href^=\"x\"]:not(.ext)::before, li:nth-child(2n) { }");
        assertEquals(List.of("a", "li"), names(structure.elements()));
        assertEquals(List.of(".ext"), names(structure.classes()));
        assertTrue(structure.ids().isEmpty());
    }

    @Test
    public void keepsWhatIsOpenAtTheEnd() {
        String text = ".a { color: red; }\n.b {\n  color: blue;\n";
        Structure structure = scan(text);
        assertEquals(List.of(".a", ".b"), names(structure.rules()));
        assertEquals(text.length(), structure.rules().get(1).end());
        assertEquals(List.of(".a", ".b"), names(structure.classes()));
    }

    @Test
    public void listsEachNameOnce() {
        Structure structure = scan(".a { } .a .b { } .b { }");
        assertEquals(List.of(".a", ".b"), names(structure.classes()));
        assertEquals(0, structure.classes().get(0).start());
        assertEquals(3, structure.rules().size());
    }

    @Test
    public void returnsNullWhenCancelled() {
        assertNull(CssScanner.scan(".a { } ".repeat(1000), () -> true));
    }
}

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
package com.gluonhq.netbeans.nbfx.api.css;

import com.gluonhq.netbeans.nbfx.api.css.CssTokens.Token;
import com.gluonhq.netbeans.nbfx.api.css.CssTokens.Type;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Checks how {@link CssTokens} reads selectors, values, numbers, strings and comments. */
public class CssTokensTest {

    private static List<String> render(String css) {
        return CssTokens.tokenize(css).stream().map(t -> t.type() + ":" + t.text()).toList();
    }

    @Test
    public void readsSelectorsAndDeclarations() {
        assertEquals(List.of("CLASS:a", "OTHER:>", "ID:root", "WORD:Text", "COLON::", "WORD:hover", "LBRACE:{",
                "WORD:-fx-fill", "COLON::", "ID:ff8800", "SEMI:;", "WORD:color", "COLON::", "WORD:white", "RBRACE:}"),
                render(".a > #root Text:hover { -fx-fill: #ff8800; color: white }"));
    }

    @Test
    public void keepsBounds() {
        String css = "  .button { }";
        Token button = CssTokens.tokenize(css).get(0);
        assertEquals(Type.CLASS, button.type());
        assertEquals(2, button.start());
        assertEquals(9, button.end());
        assertEquals(".button", css.substring(button.start(), button.end()));
    }

    @Test
    public void readsNumbersAtKeywordsAndStrings() {
        assertEquals(List.of("AT_KEYWORD:import", "WORD:url", "LPAREN:(", "STRING:\"a.css\"", "RPAREN:)", "SEMI:;",
                "WORD:1.5em", "WORD:.5em", "WORD:50", "OTHER:%", "STRING:'x\\'y'"),
                render("@import url(\"a.css\"); 1.5em .5em 50% 'x\\'y'"));
        assertTrue(CssTokens.tokenize("12").get(0).isNumber());
    }

    @Test
    public void skipsCommentsAndSurvivesUnterminatedText() {
        assertEquals(List.of("CLASS:a", "LBRACE:{", "RBRACE:}"), render("/* .x { */ .a /* } */ { }"));
        assertEquals(List.of("CLASS:a", "LBRACE:{"), render(".a { /* open"));
        assertEquals(List.of("STRING:\"open", "CLASS:b"), render("\"open\n.b"));
    }

    @Test
    public void readsEscapesAndAttributes() {
        assertEquals(List.of("CLASS:a\\:b", "LBRACKET:[", "WORD:href", "OTHER:=", "STRING:\"x\"", "RBRACKET:]"),
                render(".a\\:b[href=\"x\"]"));
    }
}

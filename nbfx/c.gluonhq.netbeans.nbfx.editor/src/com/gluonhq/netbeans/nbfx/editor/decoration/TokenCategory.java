package com.gluonhq.netbeans.nbfx.editor.decoration;

import java.util.HashMap;
import java.util.Map;

/**
 * Token categories recognized by the parsers, each mapped to a
 * style that defines how matching tokens are styled.
 */
public enum TokenCategory {

    DEFAULT("default", null),
    KEYWORD("keyword", "-fx-fill: -code-keyword-color;"),
    KEYWORD_DIRECTIVE("keyword-directive", "-fx-fill: -code-keyword-directive-color;"),
    STRING("string", "-fx-fill: -code-string-color;"),
    COMMENT("comment", "-fx-fill: -code-comment-color;"),
    IDENTIFIER("identifier", null),

    FIELD("field", "-fx-fill: -code-field-color;"),
    METHOD("method", "-fx-fill: -code-method-color; -fx-font-weight: bold;"),
    CLASS("class", "-fx-fill: -code-class-color; -fx-font-weight: bold;"),

    /** CSS selector name (element, class or ID). */
    CSS_SELECTOR("css-selector", "-fx-fill: -code-css-selector-color;"),
    /** CSS pseudo-class name. */
    CSS_PSEUDO("css-pseudo", "-fx-font-style: italic;"),
    /** CSS property name. */
    CSS_PROPERTY("css-property", "-fx-fill: -code-css-property-color; -fx-font-style: italic;"),

    /** XML element name, including the angle brackets and the {@code </} / {@code />} delimiters. */
    XML_TAG("xml-tag", "-fx-fill: -code-xml-tag-color;"),
    /** XML attribute name. */
    XML_ATTRIBUTE("xml-attribute", "-fx-fill: -code-xml-attribute-color;"),
    /** XML attribute value, including the quotes. */
    XML_VALUE("xml-value", "-fx-fill: -code-xml-value-color;"),
    /** XML comment. */
    XML_COMMENT("xml-comment", "-fx-fill: -code-comment-color;"),
    /** XML doctype declaration. */
    XML_DOCTYPE("xml-doctype", "-fx-fill: -code-xml-doctype-color; -fx-font-weight: bold;"),
    /** XML CDATA section. */
    XML_CDATA("xml-cdata-section", "-fx-fill: -code-xml-cdata-color;"),
    /** XML processing instruction {@code <?} delimiter. */
    XML_PI_START("xml-pi-start", "-fx-fill: -code-xml-pi-color;"),
    /** XML processing instruction target, e.g. {@code xml} in {@code <?xml ... ?>}. */
    XML_PI_TARGET("xml-pi-target", "-fx-fill: -code-xml-pi-color; -fx-font-weight: bold;"),
    /** XML processing instruction content. */
    XML_PI_CONTENT("xml-pi-content", "-fx-fill: -code-xml-pi-color;"),
    /** XML processing instruction {@code ?>} delimiter. */
    XML_PI_END("xml-pi-end", "-fx-fill: -code-xml-pi-color;"),

    WARNING("warning", "squiggly-warning"),
    ERROR("error", "squiggly-error"),

    BRACE_MATCH("brace-match", "brace"),
    BRACE_MISMATCH("brace-mismatch", "brace-error"),

    OCCURRENCE("occurrence", "occurrence"),

    SEARCH_MATCH("search-match", "search-match");

    /** Prefix that marks a style as a squiggly underline rather than an inline text style. */
    public static final String SQUIGGLY_PREFIX = "squiggly";

    /** Prefix that marks a style as a background highlight (used for brace matching). */
    public static final String BRACE_PREFIX = "brace";

    /** Prefix that marks a style as a background highlight for mark-occurrences. */
    public static final String OCCURRENCE_PREFIX = "occurrence";

    /** Prefix that marks a style as a background highlight for the matches of the editor's search bar. */
    public static final String SEARCH_PREFIX = "search";

    /** @return {@code true} if the style is an overlay (squiggly, brace, occurrence or search match) rather than a text style. */
    public static boolean isOverlayStyle(String style) {
        return style != null && (style.startsWith(SQUIGGLY_PREFIX) || style.startsWith(BRACE_PREFIX) ||
                    style.startsWith(OCCURRENCE_PREFIX) || style.startsWith(SEARCH_PREFIX));
    }

    private final String category;
    private final String style;

    private static final Map<String, TokenCategory> BY_CATEGORY;

    static {
        Map<String, TokenCategory> map = new HashMap<>();
        for (TokenCategory tc : values()) {
            map.put(tc.category, tc);
        }
        BY_CATEGORY = Map.copyOf(map);
    }

    TokenCategory(String category, String style) {
        this.category = category;
        this.style = style;
    }

    public String category() {
        return category;
    }

    public String style() {
        return style;
    }

    public static TokenCategory fromCategory(String category) {
        return category == null ? DEFAULT : BY_CATEGORY.get(category);
    }
}

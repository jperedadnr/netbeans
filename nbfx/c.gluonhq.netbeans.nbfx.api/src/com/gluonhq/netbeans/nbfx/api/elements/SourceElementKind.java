package com.gluonhq.netbeans.nbfx.api.elements;

/**
 * Primary semantic category of a source element, shared by the completion and
 * breadcrumbs APIs.
 *
 * <p>Renderers use this kind to choose icons and row treatment.
 */
public enum SourceElementKind {
    /** Callable member, including ordinary methods and constructors. */
    METHOD,
    /** Field-like member (field or enum constant). */
    FIELD,
    /** Type such as class, interface, enum, or record. Its subcategories are defined in {@link SourceTypeKind} */
    TYPE,
    /** Java package segment. */
    PACKAGE,
    /** Java module name (module-descriptor {@code requires}/{@code to} operands). */
    MODULE,
    /** Java keyword or literal keyword. */
    KEYWORD,
    /** Local variable/parameter-like symbol. */
    VARIABLE,
    /** Markup tag (XML/FXML element). */
    TAG,
    /** Stylesheet rule (CSS). */
    RULE,
    /** Element with no specific semantic category. */
    OTHER,

    /** Visual separator item between groups. Not an actual Java semantic type */
    SEPARATOR
}

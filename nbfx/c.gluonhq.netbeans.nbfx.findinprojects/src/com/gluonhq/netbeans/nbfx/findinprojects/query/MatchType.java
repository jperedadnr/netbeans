package com.gluonhq.netbeans.nbfx.findinprojects.query;

/** How the Containing Text of a search is read - the entries of NetBeans' "Match:" selector. */
public enum MatchType {
    /** The text is searched as typed. */
    LITERAL,
    /** {@code *} stands for any string, {@code ?} for any character, {@code \} escapes them. */
    BASIC_WILDCARDS,
    /** The text is a {@link java.util.regex.Pattern}. */
    REGEXP
}

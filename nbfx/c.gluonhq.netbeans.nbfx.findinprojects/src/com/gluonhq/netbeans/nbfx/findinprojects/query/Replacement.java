package com.gluonhq.netbeans.nbfx.findinprojects.query;

import java.util.Objects;

/**
 * The "Replace With" of a Replace in Projects search; {@code null} in a plain search.
 *
 * @param text         the replacement; {@code $1} group references in a regular-expression search
 * @param preserveCase the replacement takes the case shape of the text it replaces (ignore-case
 *                     literal and wildcard searches only)
 */
public record Replacement(String text, boolean preserveCase) {

    public Replacement {
        Objects.requireNonNull(text);
    }
}

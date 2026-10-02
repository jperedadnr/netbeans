package com.gluonhq.netbeans.nbfx.findinprojects.model;

import java.util.Objects;

/**
 * A problem met while searching that did not stop the search - a file that could not be read or
 * decoded, one too large to search. Listed under "Warnings and Errors" in the results.
 *
 * @param path    the path of the file concerned
 * @param message what went wrong, ready to show
 */
public record Issue(String path, String message) {

    public Issue {
        Objects.requireNonNull(path);
        Objects.requireNonNull(message);
    }
}

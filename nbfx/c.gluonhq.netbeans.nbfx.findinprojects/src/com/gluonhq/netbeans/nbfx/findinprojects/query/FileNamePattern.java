package com.gluonhq.netbeans.nbfx.findinprojects.query;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.openide.filesystems.FileObject;

/**
 * The "File Name Patterns" of a search - NetBeans' {@code RegexpUtil.makeFileNamePattern}: a
 * list of simple patterns separated by commas or spaces ({@code *.java, FZP??.jsp}; {@code *} any
 * string, {@code ?} any character, {@code \} an escape) matched against the file name ignoring
 * case, or, with {@code pathRegex}, one regular expression matched against the whole path
 * ({@code /}-separated) ignoring case. Empty matches every file.
 */
public record FileNamePattern(String text, boolean pathRegex) {

    public static final FileNamePattern ALL = new FileNamePattern("", false);

    public FileNamePattern {
        Objects.requireNonNull(text);
    }

    public static FileNamePattern of(String text) {
        return new FileNamePattern(text, false);
    }

    public boolean isEmpty() {
        return text.isBlank();
    }

    /** Whether a simple pattern contains a wildcard - NetBeans warns when it does not. */
    public boolean hasWildcards() {
        return text.indexOf('*') >= 0 || text.indexOf('?') >= 0;
    }

    /**
     * The compiled pattern.
     *
     * @throws PatternSyntaxException for a malformed regular expression
     */
    public Pattern toPattern() {
        if (pathRegex) {
            return Pattern.compile(text, Pattern.CASE_INSENSITIVE);
        }
        return Pattern.compile(simpleListToRegex(text), Pattern.CASE_INSENSITIVE);
    }

    /**
     * The file filter: every file when empty, else the name (or path) must match.
     *
     * @throws PatternSyntaxException for a malformed regular expression
     */
    public Predicate<FileObject> compile() {
        if (isEmpty()) {
            return fo -> true;
        }
        Pattern pattern = toPattern();
        if (pathRegex) {
            return fo -> pattern.matcher(fo.getPath()).find();
        }
        return fo -> pattern.matcher(fo.getNameExt()).matches();
    }

    /** NetBeans' {@code RegexpUtil.makeMultiRegexp}: the alternatives of the list, each a full-name match. */
    static String simpleListToRegex(String list) {
        StringBuilder sb = new StringBuilder(list.length() + 16);
        boolean quoted = false;
        boolean separator = false;
        boolean starPending = false;
        for (char c : list.toCharArray()) {
            if (quoted) {
                sb.append(Pattern.quote(String.valueOf(c)));
                quoted = false;
            } else if (c == ',' || Character.isWhitespace(c)) {
                if (starPending) {
                    sb.append(".*");
                    starPending = false;
                }
                separator = true;
            } else {
                if (separator && sb.length() > 0) {
                    sb.append('|');
                }
                separator = false;
                if (c == '?') {
                    if (starPending) {
                        sb.append(".*");
                        starPending = false;
                    }
                    sb.append('.');
                } else if (c == '*') {
                    starPending = true;
                } else {
                    if (starPending) {
                        sb.append(".*");
                        starPending = false;
                    }
                    if (c == '\\') {
                        quoted = true;
                    } else {
                        sb.append(Pattern.quote(String.valueOf(c)));
                    }
                }
            }
        }
        if (quoted) {
            sb.append("\\\\");
        } else if (starPending) {
            sb.append(".*");
        }
        return sb.toString();
    }
}

package com.gluonhq.netbeans.nbfx.findinprojects.query;

import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.api.search.TextSearch;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FileResult;
import com.gluonhq.netbeans.nbfx.findinprojects.model.Issue;
import com.gluonhq.netbeans.nbfx.findinprojects.model.TextMatch;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.netbeans.api.queries.FileEncodingQuery;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Decides whether a file can be searched and finds the search text in it - NetBeans'
 * {@code DefaultMatcher}. A file is searched when its MIME type says it is text (or an
 * {@code application/} type known to hold text), or, for an unknown type, when its first kilobyte
 * holds no {@code NUL} byte and it is not larger than 5 MiB. Text is taken from the editor when the
 * file is open ({@link OpenSources}), so unsaved edits are searched, else decoded from disk with the
 * file's {@link FileEncodingQuery encoding}; line separators are normalised to {@code '\n'}, so
 * offsets follow the editor's convention.
 */
public final class FileMatcher {

    /** NetBeans' limit for a file of unrecognised type. */
    static final long MAX_UNRECOGNIZED_FILE_SIZE = 5L * (1 << 20);
    private static final int PROBE_SIZE = 1024;

    private static final Set<String> SEARCHABLE_X_TYPES = Set.of("csh", "httpd-eruby", "httpd-php",
            "httpd-php-source", "javascript", "latex", "php", "sh", "tcl", "tex", "texinfo", "troff");
    private static final Set<String> SEARCHABLE_EXTENSIONS = Set.of("txt", "log");

    private final OpenSources openSources;

    public FileMatcher() {
        this(Lookup.getDefault().lookup(OpenSources.class));
    }

    /** With the given source of editor texts ({@code null}: disk only). */
    public FileMatcher(OpenSources openSources) {
        this.openSources = openSources;
    }

    /**
     * Whether {@code file} is a text file worth searching. Unlike NetBeans, an unknown type is
     * probed for binary content even when it is small.
     */
    public static boolean isSearchable(FileObject file) {
        String mimeType = file.getMIMEType();
        if ("content/unknown".equals(mimeType)) {
            if (SEARCHABLE_EXTENSIONS.contains(file.getExt().toLowerCase())) {
                return true;
            }
            return file.getSize() <= MAX_UNRECOGNIZED_FILE_SIZE && hasTextContent(file);
        }
        if (mimeType.startsWith("text/")) {
            return true;
        }
        if (mimeType.startsWith("application/")) {
            String subtype = mimeType.substring("application/".length());
            return subtype.equals("rtf")
                    || subtype.equals("sgml")
                    || subtype.startsWith("xml-")
                    || subtype.endsWith("+xml")
                    || subtype.equals("json")
                    || (subtype.startsWith("x-")
                    && (SEARCHABLE_X_TYPES.contains(subtype.substring(2)) || hasTextContent(file)));
        }
        return mimeType.endsWith("+xml");
    }

    /** Whether the file is too large to search when its type is unknown. */
    public static boolean isTooLarge(FileObject file) {
        return "content/unknown".equals(file.getMIMEType())
                && !SEARCHABLE_EXTENSIONS.contains(file.getExt().toLowerCase())
                && file.getSize() > MAX_UNRECOGNIZED_FILE_SIZE;
    }

    /** NetBeans' {@code DefaultMatcher.hasTextContent}: a BOM, or no {@code NUL} in the first kilobyte. */
    static boolean hasTextContent(FileObject file) {
        byte[] bytes = new byte[PROBE_SIZE];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(bytes, 0, bytes.length);
            if (read > 2 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return true;
            }
            if (read > 1 && (((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE)
                    || ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF))) {
                return true;
            }
            if (read > 3 && ((bytes[0] == 0 && bytes[1] == 0 && (bytes[2] & 0xFF) == 0xFE && (bytes[3] & 0xFF) == 0xFF)
                    || ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE && bytes[2] == 0 && bytes[3] == 0))) {
                return true;
            }
            for (int i = 0; i < read; i++) {
                if (bytes[i] == 0) {
                    return false;
                }
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * The text of {@code file} with {@code '\n'} line separators: the editor's when it is open,
     * else the file decoded with its encoding.
     *
     * @throws IOException                when the file cannot be read
     * @throws CharacterCodingException   when its bytes are not valid in its encoding
     */
    public String textOf(FileObject file) throws IOException {
        if (openSources != null) {
            String open = openSources.textOf(file);
            if (open != null) {
                return open;
            }
        }
        byte[] bytes = file.asBytes();
        Charset charset = FileEncodingQuery.getEncoding(file);
        String decoded;
        try {
            decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            if (charset.equals(StandardCharsets.UTF_8)) {
                throw ex;
            }
            // NetBeans falls back to the platform charset; be lenient the same way.
            decoded = new String(bytes, charset);
        }
        if (!decoded.isEmpty() && decoded.charAt(0) == '\uFEFF') {
            decoded = decoded.substring(1);
        }
        return normalizeLineSeparators(decoded);
    }

    /**
     * Searches {@code file} for {@code search}: its result, or {@code null} when nothing matched.
     *
     * @throws IOException when the file cannot be read or decoded
     */
    public FileResult match(TextSearch search, FileObject file) throws IOException {
        Objects.requireNonNull(search);
        String text = textOf(file);
        List<TextSearch.Match> found = search.findAll(text);
        if (found.isEmpty()) {
            return null;
        }
        return new FileResult(file, file.getSize(), file.lastModified().getTime(), toMatches(text, found));
    }

    /** A result for a file matched by name only. */
    public static FileResult nameOnly(FileObject file) {
        return new FileResult(file, file.getSize(), file.lastModified().getTime(), List.of());
    }

    /** The issue describing why {@code file} could not be searched. */
    public static Issue readIssue(FileObject file, IOException ex) {
        if (ex instanceof CharacterCodingException) {
            return new Issue(file.getPath(), NbBundle.getMessage(FileMatcher.class, "ISSUE_Encoding",
                    FileEncodingQuery.getEncoding(file).name()));
        }
        return new Issue(file.getPath(), NbBundle.getMessage(FileMatcher.class, "ISSUE_Read",
                file.getNameExt(), String.valueOf(ex.getLocalizedMessage())));
    }

    /** The issue for a file skipped because it is too large. */
    public static Issue tooLargeIssue(FileObject file) {
        return new Issue(file.getPath(), NbBundle.getMessage(FileMatcher.class, "ISSUE_TooLarge",
                file.getNameExt(), file.getSize() / 1024));
    }

    /** Positions {@code found} on its lines: line / column numbers and the line text. */
    static List<TextMatch> toMatches(String text, List<TextSearch.Match> found) {
        List<TextMatch> matches = new ArrayList<>(found.size());
        int line = 1;
        int lineStart = 0;
        int scanned = 0;
        for (TextSearch.Match match : found) {
            // Matches come in order: advance the line counter from where the last one left it.
            for (int i = scanned; i < match.start(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    lineStart = i + 1;
                }
            }
            scanned = Math.max(scanned, match.start());
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            matches.add(new TextMatch(match.start(), match.end(), line, match.start() - lineStart + 1,
                    lineStart, text.substring(lineStart, lineEnd)));
        }
        return matches;
    }

    /** {@code \r\n} and {@code \r} become {@code \n}. */
    static String normalizeLineSeparators(String text) {
        if (text.indexOf('\r') < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                sb.append('\n');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;

/**
 * Renders the line of a usage as a {@link TextFlow}: the line number, then the line's text coloured
 * by the Java lexer (keywords, strings, comments; style classes {@code usage-keyword},
 * {@code usage-string}, {@code usage-comment}) with the occurrence in bold ({@code usage-occurrence}),
 * as NetBeans' Find Usages window shows it.
 */
final class UsageLineRenderer {

    /** Identifiers the lexer does not know as keywords but the editor colours as such. */
    private static final Set<String> CONTEXTUAL_KEYWORDS = Set.of("sealed", "permits", "record", "yield");

    private UsageLineRenderer() {
    }

    static TextFlow render(Usage usage) {
        String line = usage.lineText();
        int from = clamp(usage.start() - usage.lineStart(), 0, line.length());
        int to = clamp(usage.end() - usage.lineStart(), from, line.length());
        int lead = 0;
        while (lead < from && Character.isWhitespace(line.charAt(lead))) {
            lead++;
        }
        int trail = line.length();
        while (trail > to && Character.isWhitespace(line.charAt(trail - 1))) {
            trail--;
        }

        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("usage-line");
        addText(flow, usage.line() + ": ", "usage-line-number", false);

        // A usage inside a block comment: the line alone would not lex as a comment.
        if (usage.inComment()) {
            addText(flow, line.substring(lead, from), "usage-comment", false);
            addText(flow, line.substring(from, to), "usage-comment", true);
            addText(flow, line.substring(to, trail), "usage-comment", false);
            return flow;
        }
        // Only Java lines are lexed; a style sheet's line is plain text with its occurrence marked.
        if (!"java".equalsIgnoreCase(usage.file().getExt())) {
            addText(flow, line.substring(lead, from), "usage-text", false);
            addText(flow, line.substring(from, to), "usage-text", true);
            addText(flow, line.substring(to, trail), "usage-text", false);
            return flow;
        }

        // Split at every token boundary and at the occurrence's, so each run has one style.
        TreeSet<Integer> cuts = new TreeSet<>(List.of(lead, from, to, trail));
        List<int[]> tokens = new ArrayList<>();
        List<String> styles = new ArrayList<>();
        TokenSequence<JavaTokenId> sequence = TokenHierarchy.create(line, JavaTokenId.language()).tokenSequence(JavaTokenId.language());
        if (sequence != null) {
            sequence.moveStart();
            while (sequence.moveNext()) {
                Token<JavaTokenId> token = sequence.token();
                int start = sequence.offset();
                int end = start + token.length();
                String style = styleOf(token);
                if (style != null && end > lead && start < trail) {
                    tokens.add(new int[] {start, end});
                    styles.add(style);
                    cuts.add(clamp(start, lead, trail));
                    cuts.add(clamp(end, lead, trail));
                }
            }
        }
        Integer previous = null;
        for (int cut : cuts) {
            if (previous != null && cut > previous) {
                String style = "usage-text";
                for (int i = 0; i < tokens.size(); i++) {
                    if (tokens.get(i)[0] <= previous && tokens.get(i)[1] >= cut) {
                        style = styles.get(i);
                        break;
                    }
                }
                addText(flow, line.substring(previous, cut), style, previous >= from && cut <= to);
            }
            previous = cut;
        }
        return flow;
    }

    private static String styleOf(Token<JavaTokenId> token) {
        JavaTokenId id = token.id();
        if (id == JavaTokenId.IDENTIFIER) {
            return CONTEXTUAL_KEYWORDS.contains(token.text().toString()) ? "usage-keyword" : null;
        }
        return switch (id.primaryCategory()) {
            case "keyword", "keyword-directive" -> "usage-keyword";
            case "string", "character" -> "usage-string";
            case "comment" -> "usage-comment";
            case "literal" -> "usage-keyword";
            default -> null;
        };
    }

    private static void addText(TextFlow flow, String content, String styleClass, boolean occurrence) {
        if (content.isEmpty()) {
            return;
        }
        Text text = new Text(content);
        text.getStyleClass().add(styleClass);
        if (occurrence) {
            text.getStyleClass().add("usage-occurrence");
        }
        flow.getChildren().add(text);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

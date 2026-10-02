package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsContext;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.SimpleBreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.elements.SourceElementKind;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.api.xml.lexer.XMLTokenId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Builds the breadcrumb tree of an XML/FXML source with a single lexer token walk
 * (the NetBeans XML lexer, same technique as the codefolding {@code XmlFoldDetector}):
 * every element becomes a breadcrumb labeled with its tag name — enriched with the
 * {@code fx:id} / {@code id} attribute value when present ({@code Button #login}) —
 * spanning from its {@code <} to the {@code >} of its close tag (or of the
 * self-closing {@code />}).
 *
 * <p>Markup inside comments, CDATA sections, processing instructions and attribute
 * values is ignored by the lexer itself. A close tag with no matching open tag is
 * skipped; unclosed elements extend to the start of the enclosing close tag, or to
 * the end of the file — so breadcrumbs stay useful while the document is mid-edit.</p>
 */
final class XmlBreadcrumbsScanner {

    private XmlBreadcrumbsScanner() {
    }

    /** Mutable element node used while the lexer walk is in progress. */
    private static final class Node {

        final String name;
        final int start;
        String id;
        int end = -1;
        final List<Node> children = new ArrayList<>();

        Node(String name, int start) {
            this.name = name;
            this.start = start;
        }

        String label() {
            return BreadcrumbsElements.condense(id == null ? name : name + " #" + id);
        }
    }

    /**
     * Lexes {@code context.documentText()} and returns one breadcrumb root per document
     * root element, or {@code null} when the scan was canceled.
     */
    static List<BreadcrumbElement> scan(BreadcrumbsContext context, Cancellation cancellation) {
        String source = context.documentText();
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        TokenSequence<XMLTokenId> ts = TokenHierarchy.create(source, XMLTokenId.language())
                .tokenSequence(XMLTokenId.language());
        if (ts == null) {
            return List.of();
        }

        Node documentRoot = new Node(null, 0);
        Deque<Node> openElements = new ArrayDeque<>();
        openElements.push(documentRoot);
        // the element whose "<name" was seen but whose ">"/"/>" hasn't arrived yet
        Node pendingOpen = null;
        // the just-closed element still waiting for the ">" of its close tag
        Node pendingClose = null;
        String pendingAttribute = null;

        ts.moveStart();
        while (ts.moveNext()) {
            if (cancellation.isCancelled()) {
                return null;
            }
            Token<XMLTokenId> token = ts.token();
            int offset = ts.offset();
            switch (token.id()) {
                case TAG -> {
                    String text = token.text().toString();
                    if (text.startsWith("</")) {
                        String name = text.endsWith(">")
                                ? text.substring(2, text.length() - 1) : text.substring(2);
                        pendingClose = closeElement(name, offset, offset + text.length(),
                                openElements, documentRoot);
                        if (text.endsWith(">")) {
                            pendingClose = null;
                        }
                        pendingOpen = null;
                    } else if (text.startsWith("<")) {
                        pendingOpen = new Node(text.substring(1), offset);
                        pendingClose = null;
                    } else {
                        // the open/close tag's end delimiter: ">" opens the element,
                        // "/>" self-closes it
                        if (pendingOpen != null) {
                            openElements.peek().children.add(pendingOpen);
                            if ("/>".equals(text)) {
                                pendingOpen.end = offset + text.length();
                            } else {
                                openElements.push(pendingOpen);
                            }
                        } else if (pendingClose != null && ">".equals(text)) {
                            pendingClose.end = offset + 1;
                        }
                        pendingOpen = null;
                        pendingClose = null;
                    }
                    pendingAttribute = null;
                }
                case ARGUMENT -> pendingAttribute = token.text().toString();
                case VALUE -> {
                    if (pendingOpen != null && pendingOpen.id == null
                            && ("fx:id".equals(pendingAttribute) || "id".equals(pendingAttribute))) {
                        pendingOpen.id = unquote(token.text().toString());
                    }
                }
                default -> { }
            }
        }
        // Unclosed elements (mid-edit) extend to the end of the file.
        while (openElements.peek() != documentRoot) {
            Node unclosed = openElements.pop();
            if (unclosed.end < 0) {
                unclosed.end = source.length();
            }
        }
        return toElements(documentRoot);
    }

    /**
     * Closes the innermost open element named {@code name}: unclosed intermediates are popped
     * and truncated at the close tag's start. Returns the closed node (its end is provisionally
     * the close-tag token end, extended later when the {@code >} arrives as a separate token),
     * or {@code null} when no open element matches.
     */
    private static Node closeElement(String name, int closeStart, int closeEnd,
                                     Deque<Node> openElements, Node documentRoot) {
        if (openElements.stream().noneMatch(node -> node != documentRoot && name.equals(node.name))) {
            return null;
        }
        Node node;
        do {
            node = openElements.pop();
            node.end = name.equals(node.name) ? closeEnd : closeStart;
        } while (!name.equals(node.name));
        return node;
    }

    /** Converts the mutable node tree into immutable breadcrumb elements. */
    private static List<BreadcrumbElement> toElements(Node documentRoot) {
        List<BreadcrumbElement> roots = new ArrayList<>();
        for (Node node : documentRoot.children) {
            SimpleBreadcrumbElement root = SimpleBreadcrumbElement.root(node.label(),
                    node.start, node.end, SourceElementKind.TAG, null, 0);
            addChildren(root, node);
            roots.add(root);
        }
        return List.copyOf(roots);
    }

    private static void addChildren(SimpleBreadcrumbElement parent, Node node) {
        for (Node child : node.children) {
            addChildren(parent.addChild(child.label(), child.start, child.end,
                    SourceElementKind.TAG, null, 0), child);
        }
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && (trimmed.charAt(0) == '"' || trimmed.charAt(0) == '\'')
                && trimmed.charAt(trimmed.length() - 1) == trimmed.charAt(0)) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}

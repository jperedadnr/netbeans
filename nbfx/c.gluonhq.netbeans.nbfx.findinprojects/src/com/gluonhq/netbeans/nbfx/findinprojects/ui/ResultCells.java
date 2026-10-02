package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.findinprojects.model.TextMatch;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.Detail;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.File;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.Folder;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.Summary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * The columns of the results table and how each kind of {@link ResultRow} is drawn in them:
 * <b>Name</b> (the tree column: the summary sentence, folders and files with their icons, the
 * occurrences as their line with the match in bold), <b>Matches</b>, <b>Path</b>, <b>Size</b> and
 * <b>Modified</b>. The text of a detail row is computed by {@link #detailSpans(TextMatch)}, which
 * has no JavaFX in it, so it can be tested on its own.
 */
final class ResultCells {

    static final String NAME = "name";
    static final String MATCHES = "matches";
    static final String PATH = "path";
    static final String SIZE = "size";
    static final String MODIFIED = "modified";

    /** Beyond this many characters after the occurrence, a line is cut with an ellipsis. */
    static final int TAIL_LIMIT = 200;

    /** A piece of a detail row's text. */
    record Span(String text, Style style) {

        enum Style {
            LINE_NUMBER, TEXT, OCCURRENCE, COLUMN
        }
    }

    private static final DateTimeFormatter MODIFIED_FORMAT =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault());

    private ResultCells() {
    }

    // --- columns -----------------------------------------------------------------------------

    /** Whether the matches under a row are to be replaced: all, none, or some (a file or folder row). */
    enum CheckState {
        CHECKED, UNCHECKED, INDETERMINATE
    }

    /** The check boxes of a replace search's rows: what they show and what a click does. */
    interface Checks {

        CheckState stateOf(TreeItem<ResultRow> item);

        /** Checks or unchecks every match under {@code item}. */
        void setChecked(TreeItem<ResultRow> item, boolean checked);
    }

    /**
     * The tree column. {@code showIssues} runs when the "Warnings and Errors" link of the summary
     * row is clicked; with {@code checks} (a replace search) every folder, file and occurrence row
     * carries a check box.
     */
    static TreeTableColumn<ResultRow, ResultRow> nameColumn(Runnable showIssues, Checks checks) {
        TreeTableColumn<ResultRow, ResultRow> column = column(NAME, "COL_Name", 320);
        // the column that stretches with the table: never squeezed below a readable width
        column.setMinWidth(200);
        // always shown: the table's column menu disables the entry of a column whose visibility is bound
        column.visibleProperty().bind(new ReadOnlyBooleanWrapper(true));
        column.setCellFactory(c -> new NameCell(showIssues, checks));
        column.setComparator(Comparator.comparing(ResultCells::nameText, String.CASE_INSENSITIVE_ORDER));
        return column;
    }

    static TreeTableColumn<ResultRow, ResultRow> matchesColumn() {
        TreeTableColumn<ResultRow, ResultRow> column = column(MATCHES, "COL_Matches", 90);
        column.setCellFactory(c -> new TextCell(ResultCells::matchesText, null));
        column.setComparator(Comparator.comparingInt(ResultCells::matchCount));
        return column;
    }

    static TreeTableColumn<ResultRow, ResultRow> pathColumn() {
        TreeTableColumn<ResultRow, ResultRow> column = column(PATH, "COL_Path", 220);
        column.setCellFactory(c -> new TextCell(ResultCells::pathText, ResultCells::fullPath));
        column.setComparator(Comparator.comparing(ResultCells::pathText, String.CASE_INSENSITIVE_ORDER));
        return column;
    }

    static TreeTableColumn<ResultRow, ResultRow> sizeColumn() {
        TreeTableColumn<ResultRow, ResultRow> column = column(SIZE, "COL_Size", 84);  // not 80, the default the skin auto-sizes
        column.setCellFactory(c -> new TextCell(ResultCells::sizeText, null));
        column.setComparator(Comparator.comparingLong(ResultCells::size));
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        return column;
    }

    static TreeTableColumn<ResultRow, ResultRow> modifiedColumn() {
        TreeTableColumn<ResultRow, ResultRow> column = column(MODIFIED, "COL_Modified", 140);
        column.setCellFactory(c -> new TextCell(ResultCells::modifiedText, null));
        column.setComparator(Comparator.comparingLong(ResultCells::modified));
        return column;
    }

    /** A column hidden until the user turns it on in the table's column menu; Name binds itself visible. */
    private static TreeTableColumn<ResultRow, ResultRow> column(String id, String titleKey, double width) {
        TreeTableColumn<ResultRow, ResultRow> column = new TreeTableColumn<>(message(titleKey));
        column.setId(id);
        column.setPrefWidth(width);
        column.setMinWidth(40);
        column.setVisible(false);
        column.setCellValueFactory(features -> {
            ObservableValue<ResultRow> value = features.getValue().valueProperty();
            return value;
        });
        return column;
    }

    // --- texts -------------------------------------------------------------------------------

    /** The text a row shows in the Name column: the summary sentence, a name, or a line. */
    static String nameText(ResultRow row) {
        return switch (row) {
            case Summary summary -> summary.text();
            case Folder folder -> folder.name();
            case File file -> file.name();
            case Detail detail -> detail.match().lineText().strip();
        };
    }

    /** "(N matches)" on folder and file rows - NetBeans' {@code TEXT_NUM_MATCHES_IN_NODE} -, nothing elsewhere. */
    static String matchesText(ResultRow row) {
        return switch (row) {
            case Folder folder -> message("LBL_Matches", folder.matches());
            case File file -> file.result().matches().isEmpty() ? "" : message("LBL_Matches", file.matches().size());
            default -> "";
        };
    }

    private static int matchCount(ResultRow row) {
        return switch (row) {
            case Folder folder -> folder.matches();
            case File file -> file.matches().size();
            default -> -1;
        };
    }

    /** The folder of a file relative to its scope root; nothing on the other rows. */
    static String pathText(ResultRow row) {
        return row instanceof File file ? file.relativeFolder() : "";
    }

    private static String fullPath(ResultRow row) {
        return row.file() == null ? null : FileUtil.getFileDisplayName(row.file());
    }

    static String sizeText(ResultRow row) {
        return row instanceof File file ? humanSize(file.result().size()) : "";
    }

    private static long size(ResultRow row) {
        return row instanceof File file ? file.result().size() : -1;
    }

    static String modifiedText(ResultRow row) {
        return row instanceof File file ? MODIFIED_FORMAT.format(Instant.ofEpochMilli(file.result().lastModified())) : "";
    }

    private static long modified(ResultRow row) {
        return row instanceof File file ? file.result().lastModified() : -1;
    }

    /** {@code 512 B}, {@code 1.5 KB}, {@code 12.0 MB}... */
    static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes / 1024.0;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format("%.1f %s", value, units[unit]);
    }

    /** NetBeans' {@code TEXT_DETAIL_FMT_FULL1}: "[text at line L, column C]". */
    static String detailTooltip(Detail detail) {
        TextMatch match = detail.match();
        String text = match.lineText();
        int start = Math.max(0, match.start() - match.lineStart());
        int end = Math.min(text.length(), match.end() - match.lineStart());
        String found = start < end ? text.substring(start, end) : "";
        return message("TIP_Detail", found, match.line(), match.column());
    }

    /**
     * The pieces of a detail row: the line number, the line's text with its indentation removed
     * and the occurrence as its own span (a match that runs past the end of the line is emphasised
     * to the end of it), the tail cut at {@link #TAIL_LIMIT} characters, and - as NetBeans - the
     * column the occurrence starts at, "[column C]".
     */
    static List<Span> detailSpans(TextMatch match) {
        String line = match.lineText();
        int start = Math.max(0, Math.min(line.length(), match.start() - match.lineStart()));
        int end = Math.max(start, Math.min(line.length(), match.end() - match.lineStart()));
        int lead = 0;
        while (lead < line.length() && Character.isWhitespace(line.charAt(lead))) {
            lead++;
        }
        // keep a match that starts inside the indentation
        lead = Math.min(lead, start);
        String shown = line.substring(lead).stripTrailing();
        start -= lead;
        end = Math.min(end - lead, shown.length());
        String tail = shown.substring(end);
        if (tail.length() > TAIL_LIMIT) {
            tail = tail.substring(0, TAIL_LIMIT) + "…";
        }
        List<Span> spans = new ArrayList<>(4);
        spans.add(new Span(message("LBL_Line", match.line()) + " ", Span.Style.LINE_NUMBER));
        if (start > 0) {
            spans.add(new Span(shown.substring(0, start), Span.Style.TEXT));
        }
        if (end > start) {
            spans.add(new Span(shown.substring(start, end), Span.Style.OCCURRENCE));
        }
        if (!tail.isEmpty()) {
            spans.add(new Span(tail, Span.Style.TEXT));
        }
        spans.add(new Span("  " + message("LBL_Column", match.column()), Span.Style.COLUMN));
        return spans;
    }

    // --- cells -------------------------------------------------------------------------------

    /** The Name column: icon and text for folders and files, a {@link TextFlow} for the summary and the details. */
    private static final class NameCell extends TreeTableCell<ResultRow, ResultRow> {

        private final Runnable showIssues;
        private final Checks checks;
        /** The check box of a replace search's row, {@code null} in a plain search. */
        private final CheckBox check;
        /** The check box with the row's graphic after it; the box is built once and the graphic swapped in its slot. */
        private final HBox box;
        private final HBox slot = new HBox();

        NameCell(Runnable showIssues, Checks checks) {
            this.showIssues = showIssues;
            this.checks = checks;
            getStyleClass().add("result-cell");
            if (checks == null) {
                check = null;
                box = null;
            } else {
                check = new CheckBox();
                check.getStyleClass().add("result-check");
                check.setFocusTraversable(false);
                // a partly checked box is shown indeterminate but unselected: a click checks everything under it
                check.setOnAction(e -> checks.setChecked(getTreeTableRow().getTreeItem(), check.isSelected()));
                slot.setAlignment(Pos.CENTER_LEFT);
                slot.setMinWidth(0);
                HBox.setHgrow(slot, javafx.scene.layout.Priority.ALWAYS);
                box = new HBox(4, check, slot);
                box.setAlignment(Pos.CENTER_LEFT);
                box.setMinWidth(0);
            }
        }

        @Override
        protected void updateItem(ResultRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            switch (row) {
                case Summary summary -> {
                    setText(null);
                    setGraphic(summaryFlow(summary, showIssues));
                    setTooltip(null);
                }
                case Folder folder -> {
                    setText(folder.name());
                    setGraphic(checked(folder.scopeRoot() ? SearchIcons.projectIcon(folder.folder()) : SearchIcons.folder()));
                    setTooltip(new Tooltip(FileUtil.getFileDisplayName(folder.folder())));
                }
                case File file -> {
                    setText(file.name());
                    setGraphic(checked(SearchIcons.fileIcon(file.file())));
                    setTooltip(new Tooltip(FileUtil.getFileDisplayName(file.file())));
                }
                case Detail detail -> {
                    setText(null);
                    setGraphic(checked(iconAndLine(SearchIcons.view("textDetail"), detailFlow(detail.match()))));
                    setTooltip(new Tooltip(detailTooltip(detail)));
                }
            }
        }

        /**
         * {@code graphic} behind the row's check box, showing the state of the matches under the
         * row; the check box alone for a row without an icon ({@code null}: no provider has one).
         */
        private Node checked(Node graphic) {
            if (check == null) {
                return graphic;
            }
            CheckState state = checks.stateOf(getTreeTableRow().getTreeItem());
            check.setSelected(state == CheckState.CHECKED);
            check.setIndeterminate(state == CheckState.INDETERMINATE);
            if (graphic == null) {
                slot.getChildren().clear();
            } else {
                slot.getChildren().setAll(graphic);
            }
            return box;
        }
    }

    /** A text column: the text of {@code text}, the tooltip of {@code tooltip} when it gives one. */
    private static final class TextCell extends TreeTableCell<ResultRow, ResultRow> {

        private final Function<ResultRow, String> text;
        private final Function<ResultRow, String> tooltip;

        TextCell(Function<ResultRow, String> text, Function<ResultRow, String> tooltip) {
            this.text = text;
            this.tooltip = tooltip;
            getStyleClass().add("result-cell");
        }

        @Override
        protected void updateItem(ResultRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(text.apply(row));
            String tip = tooltip == null ? null : tooltip.apply(row);
            setTooltip(tip == null || tip.isEmpty() ? null : new Tooltip(tip));
        }
    }

    /**
     * The find icon next to the summary sentence, with the search text in bold and the issues
     * link when there are any. The icon sits outside the {@link TextFlow}, as the detail rows'
     * marker does: a flow lays a node out inline on the text baseline, with no spacing after it.
     */
    static HBox summaryFlow(Summary summary, Runnable showIssues) {
        return iconAndLine(SearchIcons.view("find"), summaryText(summary, showIssues));
    }

    /**
     * An icon and a one-line {@link TextFlow} side by side, centred on each other. The flow keeps
     * the width of its text on one line however narrow the column gets - a flow wraps as soon as
     * it is squeezed, which would put a second line into a fixed-height row - and the box clips
     * what does not fit, as a label does.
     */
    private static HBox iconAndLine(Node icon, TextFlow flow) {
        flow.setMinWidth(Region.USE_PREF_SIZE);
        HBox box = new HBox(4, icon, flow);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(box.widthProperty());
        clip.heightProperty().bind(box.heightProperty());
        box.setClip(clip);
        return box;
    }

    /** The summary sentence with the search text in bold, followed by the issues link when there are any. */
    static TextFlow summaryText(Summary summary, Runnable showIssues) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("result-summary");
        flow.getChildren().add(text(summary.prefix(), "result-text"));
        if (summary.emphasis() != null) {
            flow.getChildren().add(text(summary.emphasis(), "result-occurrence"));
        }
        if (!summary.suffix().isEmpty()) {
            flow.getChildren().add(text(summary.suffix(), "result-text"));
        }
        if (summary.replacement() != null) {
            flow.getChildren().addAll(text(summary.replacementPrefix(), "result-text"),
                    text(summary.replacement(), "result-occurrence"), text(".", "result-text"));
        }
        if (summary.issues() > 0) {
            Hyperlink link = new Hyperlink(message("LNK_Issues", summary.issues()));
            link.getStyleClass().add("result-issues-link");
            link.setFocusTraversable(false);
            link.setOnAction(e -> {
                link.setVisited(false);
                if (showIssues != null) {
                    showIssues.run();
                }
            });
            flow.getChildren().addAll(text(" ", "result-text"), link);
        }
        return flow;
    }

    /** The line of an occurrence, as {@link #detailSpans}. */
    static TextFlow detailFlow(TextMatch match) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("result-line");
        for (Span span : detailSpans(match)) {
            flow.getChildren().add(text(span.text(), switch (span.style()) {
                case LINE_NUMBER -> "result-line-number";
                case TEXT -> "result-text";
                case OCCURRENCE -> "result-occurrence";
                case COLUMN -> "result-line-column";
            }));
        }
        return flow;
    }

    private static Node text(String content, String styleClass) {
        Text text = new Text(Objects.requireNonNull(content));
        text.getStyleClass().add(styleClass);
        return text;
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(ResultCells.class, key, args);
    }
}

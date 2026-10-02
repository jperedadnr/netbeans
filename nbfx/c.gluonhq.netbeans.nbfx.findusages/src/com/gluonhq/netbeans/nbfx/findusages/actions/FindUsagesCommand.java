package com.gluonhq.netbeans.nbfx.findusages.actions;

import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.findusages.model.UsagesModel;
import com.gluonhq.netbeans.nbfx.findusages.query.CssTarget;
import com.gluonhq.netbeans.nbfx.findusages.query.UsagesQuery;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsagesViewProvider;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.util.Subscription;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * Edit ▸ Find Usages (Ctrl+F7): finds the usages of the element at the caret of the active Java
 * document, then shows them in a new tab of the Usages view. Enabled while the active document is a
 * Java file.
 */
final class FindUsagesCommand extends AbstractCommand {

    private static final Logger LOG = Logger.getLogger(FindUsagesCommand.class.getName());
    static final KeyCombination SHORTCUT = new KeyCodeCombination(KeyCode.F7, KeyCombination.CONTROL_DOWN);

    private final ObservableValue<EditorDocument> activeDocument;
    private final UsagesModel model;
    private final Subscription subscription;

    FindUsagesCommand(ObservableValue<EditorDocument> activeDocument, UsagesModel model) {
        super(ActionIds.FIND_USAGES, NbBundle.getMessage(FindUsagesCommand.class, "CTL_FindUsagesCommand"),
                SHORTCUT);
        this.activeDocument = activeDocument;
        this.model = model;
        this.subscription = activeDocument.subscribe(doc -> setDisabled(!isSearchable(doc)));
    }

    /** Whether Find Usages can start from {@code document}: a Java source or a style sheet. */
    static boolean isSearchable(EditorDocument document) {
        FileObject file = document == null ? null : document.getFileObject();
        return file != null && ("java".equalsIgnoreCase(file.getExt()) || CssTarget.isStyleSheet(file));
    }

    @Override
    public void run() {
        EditorDocument document = activeDocument.getValue();
        if (!isSearchable(document)) {
            return;
        }
        FileObject file = document.getFileObject();
        String noTarget = CssTarget.isStyleSheet(file) ? "ERR_NoCssElementAtCaret" : "ERR_NoElementAtCaret";
        show(model.findUsages(file, document.getCaretOffset()), NbBundle.getMessage(FindUsagesCommand.class, noTarget));
    }

    /**
     * Shows the query {@code pending} resolves to in the Usages view, or reports why there is none:
     * {@code noTarget} when nothing searchable was found, the failure otherwise.
     */
    static void show(CompletableFuture<UsagesQuery> pending, String noTarget) {
        pending.whenComplete((query, failure) -> Platform.runLater(() -> onResolved(query, failure, noTarget)));
    }

    private static void onResolved(UsagesQuery query, Throwable failure, String noTarget) {
        if (failure != null) {
            LOG.log(Level.WARNING, "Find Usages failed", failure);
            ErrorReporter.report(NbBundle.getMessage(FindUsagesCommand.class, "CTL_FindUsagesCommand"),
                    NbBundle.getMessage(FindUsagesCommand.class, "ERR_FindUsages"), null, failure);
            return;
        }
        if (query == null) {
            ErrorReporter.report(NbBundle.getMessage(FindUsagesCommand.class, "CTL_FindUsagesCommand"), null, noTarget);
            return;
        }
        UsagesViewProvider view = UsagesViewProvider.instance();
        if (view != null) {
            view.show(query);
        }
    }

    @Override
    public void dispose() {
        subscription.unsubscribe();
    }
}

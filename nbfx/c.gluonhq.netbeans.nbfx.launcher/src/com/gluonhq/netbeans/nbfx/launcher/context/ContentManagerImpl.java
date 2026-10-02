package com.gluonhq.netbeans.nbfx.launcher.context;

import com.gluonhq.netbeans.nbfx.launcher.session.CloseConfirmation;
import com.gluonhq.netbeans.nbfx.launcher.ui.NbfxTabPane;
import com.gluonhq.netbeans.nbfx.launcher.ui.TabContextMenu;
import com.gluonhq.netbeans.nbfx.launcher.ui.TabTitles;

import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.EditorService;
import com.gluonhq.netbeans.nbfx.api.file.FileIconProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = ContentManager.class)
public class ContentManagerImpl implements ContentManager {

    static final Logger LOG = Logger.getLogger(ContentManagerImpl.class.getName());

    /** Tooltip for an editor tab's close button, advertising its modifier-click shortcuts. */
    private static final String EDITOR_CLOSE_TOOLTIP =
            NbBundle.getMessage(ContentManagerImpl.class, "Tab.editorClose.tooltip",
                    NbfxTabPane.SYM_SHIFT, NbfxTabPane.SYM_OPT);

    /** Pseudoclass active on a tab's title label while its document does not accept edits. */
    private static final PseudoClass READONLY = PseudoClass.getPseudoClass("readonly");

    private final TabPane mainPane;

    public ContentManagerImpl() {
        LOG.info("Creating main pane");
        mainPane = new NbfxTabPane(NbfxTabPane.PaneRole.MAIN);
    }

    public Region getMainPane() {
        return this.mainPane;
    }

    @Override
    public void openFile(FileObject file, Node graphic) {
        if (file == null || !file.isData()) {
            return;
        }
        // Search across all TabPanes (main + detached windows) for an existing tab.
        NbfxTabPane.findTab(file).ifPresentOrElse(
            NbfxTabPane::selectTabAndMoveToFront,
            () -> {
                EditorService editorService = Lookup.getDefault().lookup(EditorService.class);
                if (editorService == null) {
                    LOG.warning("No EditorService found; cannot open " + file);
                    return;
                }
                attachNewTab(editorService.createDocument(file), graphic);
            });
    }

    @Override
    public void openFile(FileObject file, int start, int end) {
        if (file == null || !file.isData()) {
            return;
        }
        Tab existing = NbfxTabPane.findTab(file).orElse(null);
        if (existing != null) {
            NbfxTabPane.selectTabAndMoveToFront(existing);
            EditorDocument document = NbfxTabPane.documentOf(existing);
            if (document != null) {
                document.selectRange(start, end);
            }
            return;
        }
        EditorService editorService = Lookup.getDefault().lookup(EditorService.class);
        if (editorService == null) {
            LOG.warning("No EditorService found; cannot open " + file);
            return;
        }
        EditorDocument document = editorService.createDocument(file);
        attachNewTab(document, null);
        // After the tab is attached (same deferred step), so the selection scrolls into view
        Platform.runLater(() -> document.selectRange(start, end));
    }

    /** Builds a tab for a freshly created {@code document} and adds it to the pane editors open in. */
    private void attachNewTab(EditorDocument document, Node graphic) {
        LOG.info("Open tab named " + document.getTitle() + " with content " + document.getNode());
        Platform.runLater(() -> {
            EditorContext editorContext = Lookup.getDefault().lookup(EditorContext.class);
            Tab tab = createTab(document, graphic, editorContext);
            NbfxTabPane.attachTab(NbfxTabPane.editorHome(mainPane, editorContext), tab);
        });
    }

    @Override
    public EditorDocument cloneDocument(EditorDocument document) {
        Tab original = document == null ? null : NbfxTabPane.findTab(document).orElse(null);
        TabPane pane = original == null ? null : original.getTabPane();
        if (pane == null) {
            return null;
        }
        EditorDocument clone = document.cloneDocument();
        if (clone == null) {
            return null;
        }
        EditorContext editorContext = Lookup.getDefault().lookup(EditorContext.class);
        Tab tab = createTab(clone, null, editorContext);
        NbfxTabPane.attachTabAt(pane, tab, pane.getTabs().indexOf(original) + 1);
        clone.restoreView(document.getTopParagraph(), document.getCaretParagraph(), document.getCaretColumn());
        return clone;
    }

    /** Builds the editor tab of {@code document} and registers the document with the editor context. */
    private Tab createTab(EditorDocument document, Node graphic, EditorContext editorContext) {
        FileObject fileObject = document.getFileObject();
        ObservableList<EditorDocument> openDocuments = editorContext != null
                ? editorContext.getDocuments() : FXCollections.emptyObservableList();
        Tab tab = new Tab();
        Label titleLabel = new Label();
        titleLabel.getStyleClass().add("tab-title");
        // Gray the title out (":readonly" styling) while the document does not accept edits,
        // e.g. when it is locked after a conflicting external modification.
        document.editableProperty().subscribe(editable ->
                titleLabel.pseudoClassStateChanged(READONLY, !Boolean.TRUE.equals(editable)));
        // Prefix the title with a dirty marker while the document has unsaved changes, and let
        // it follow the open documents: a file whose name also exists in another open project
        // is shown with its project name.
        titleLabel.textProperty().bind(Bindings.createStringBinding(
                () -> (document.isModified() ? "*" : "") + TabTitles.titleFor(document, openDocuments),
                document.modifiedProperty(), openDocuments));
        // Callers that have no icon at hand (session restore, clones) pass none; fall back to the
        // shared provider so every tab for a given file looks the same however it was opened.
        Node icon = graphic != null ? graphic : iconFor(fileObject);
        if (icon != null) {
            titleLabel.setGraphic(icon);
        }
        tab.setGraphic(titleLabel);
        tab.setContent(document.getNode());
        NbfxTabPane.installTabLabelTooltip(tab, TabTitles.tooltipFor(document));
        tab.setUserData(fileObject);
        tab.setOnCloseRequest(event -> {
            if (NbfxTabPane.handleEditorCloseModifiers(event, document)) {
                return;
            }
            if (!CloseConfirmation.confirmClose(List.of(document))) {
                event.consume();
            }
        });
        NbfxTabPane.setDocument(tab, document);
        NbfxTabPane.installCloseButtonTooltip(tab, EDITOR_CLOSE_TOOLTIP);
        TabContextMenu.installForEditor(tab);
        if (editorContext != null) {
            editorContext.register(document);
        }
        return tab;
    }

    /** The icon for {@code file} from the registered {@link FileIconProvider}, or {@code null}. */
    private static Node iconFor(FileObject file) {
        FileIconProvider icons = Lookup.getDefault().lookup(FileIconProvider.class);
        return icons == null ? null : icons.createIcon(file);
    }

    @Override
    public void closeAll() {
        NbfxTabPane.closeAllTabs();
    }

    @Override
    public void closeFiles(String projectPath) {
        // Which documents belong to the project is resolved now, not when the tabs are actually
        // removed: a document resolves its project lazily, from the registry, and the removal is
        // deferred to the FX thread - by then the project is already closed (closeProject asks for
        // its files first) and its documents would answer with no project, leaving their tabs open.
        Set<EditorDocument> targets = Collections.newSetFromMap(new IdentityHashMap<>());
        targets.addAll(documentsOf(projectPath));
        NbfxTabPane.closeTabs(targets::contains);
    }

    @Override
    public List<EditorDocument> getMainPaneDocuments() {
        return NbfxTabPane.documentsOf(mainPane);
    }

    @Override
    public List<EditorDocument> documentsOf(String projectPath) {
        return NbfxTabPane.allDocuments().stream()
                .filter(document -> Objects.equals(projectPath, document.getProjectPath()))
                .toList();
    }

    @Override
    public FileObject getActiveFile() {
        Tab selected = mainPane.getSelectionModel().getSelectedItem();
        return selected != null && selected.getUserData() instanceof FileObject fo ? fo : null;
    }

    @Override
    public void selectFile(FileObject file) {
        if (file == null) {
            return;
        }
        for (Tab tab : mainPane.getTabs()) {
            if (file.equals(tab.getUserData())) {
                mainPane.getSelectionModel().select(tab);
                return;
            }
        }
    }

    @Override
    public void focusActiveEditor() {
        NbfxTabPane.focusSelectedTab(mainPane);
    }

    @Override
    public void closeFile(FileObject file) {
        if (file == null) {
            return;
        }
        NbfxTabPane.closeTabs(document -> file.equals(document.getFileObject()));
    }

    @Override
    public void closeDocument(EditorDocument document) {
        if (document != null) {
            NbfxTabPane.findTab(document).ifPresent(NbfxTabPane::closeTab);
        }
    }

    @Override
    public void deleteFile(FileObject file) throws IOException {
        if (file == null) {
            return;
        }
        file.delete();
        closeFile(file);
    }

    @Override
    public void deleteFolder(FileObject folder) throws IOException {
        if (folder == null) {
            return;
        }
        // Collect the open files under the folder before deleting it, then close their tabs.
        List<FileObject> openFiles = new ArrayList<>();
        collectOpenFiles(folder, openFiles);
        folder.delete();
        openFiles.forEach(this::closeFile);
    }

    private void collectOpenFiles(FileObject folder, List<FileObject> out) {
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                collectOpenFiles(child, out);
            } else if (documentForFile(child) != null) {
                out.add(child);
            }
        }
    }

    @Override
    public ObservableValue<EditorDocument> mainPaneActiveDocument() {
        return mainPane.getSelectionModel().selectedItemProperty().map(NbfxTabPane::documentOf);
    }

    @Override
    public EditorDocument documentForFile(FileObject file) {
        return file == null ? null
                : NbfxTabPane.findTab(file).map(NbfxTabPane::documentOf).orElse(null);
    }
}

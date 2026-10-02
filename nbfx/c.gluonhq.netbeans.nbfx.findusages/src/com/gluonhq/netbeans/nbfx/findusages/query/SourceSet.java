package com.gluonhq.netbeans.nbfx.findusages.query;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * The sources a query searches: a set of source roots (by default those of every open project, as
 * the projects register them in the {@link GlobalPathRegistry}), optionally narrowed to a set of
 * files ({@link QueryOptions.Scope#OPEN_FILES}), read through {@link OpenSources} so files open in
 * an editor are seen with their current text.
 * <p>
 * Candidate files are found by a textual pre-filter - the files mentioning the target's name - the
 * job NetBeans' usages index does; javac then confirms each occurrence. Every open file is compiled
 * from an in-memory copy of its editor text ({@link MemorySources}), which also puts the file's
 * offsets in the editor's {@code '\n'}-counted convention; files read from disk are converted.
 */
public final class SourceSet {

    private static final Logger LOG = Logger.getLogger(SourceSet.class.getName());

    private final Set<FileObject> roots;
    /** The only files searched under the roots, {@code null} for all of them. */
    private final Set<FileObject> files;
    private final OpenSources openSources;

    private SourceSet(Collection<FileObject> roots, Set<FileObject> files, OpenSources openSources) {
        this.roots = new LinkedHashSet<>(roots);
        this.files = files == null ? null : Set.copyOf(files);
        this.openSources = openSources;
    }

    /** The source roots of all open projects, with the editors' text from the {@link OpenSources} in the default Lookup. */
    public static SourceSet forOpenProjects() {
        return new SourceSet(GlobalPathRegistry.getDefault().getSourceRoots(), null,
                Lookup.getDefault().lookup(OpenSources.class));
    }

    /**
     * The sources {@code scope} names for a query started in {@code file}: every open project's
     * roots, those of the project owning {@code file} (else the file's own root), or every root
     * with the search limited to the files of {@code file}'s folder, to {@code file} itself, or to
     * the files open in editors - which the {@link ContentManager} lists, so that scope is built
     * on the JavaFX thread.
     */
    public static SourceSet forScope(QueryOptions.Scope scope, FileObject file) {
        Set<FileObject> allRoots = GlobalPathRegistry.getDefault().getSourceRoots();
        OpenSources openSources = Lookup.getDefault().lookup(OpenSources.class);
        return switch (scope) {
            case ALL_PROJECTS -> new SourceSet(allRoots, null, openSources);
            case CURRENT_PROJECT -> new SourceSet(projectRoots(allRoots, file), null, openSources);
            case CURRENT_PACKAGE -> new SourceSet(allRoots, packageFiles(file), openSources);
            case CURRENT_FILE -> new SourceSet(allRoots, Set.of(file), openSources);
            case OPEN_FILES -> new SourceSet(allRoots, openFiles(), openSources);
        };
    }

    /** The given roots; {@code openSources} may be {@code null} to read every file from disk. */
    public static SourceSet of(Collection<FileObject> roots, OpenSources openSources) {
        return new SourceSet(roots, null, openSources);
    }

    /** Like {@link #of(Collection, OpenSources)}, searching only {@code files} among the roots' files. */
    public static SourceSet of(Collection<FileObject> roots, Set<FileObject> files, OpenSources openSources) {
        return new SourceSet(roots, files, openSources);
    }

    private static Set<FileObject> projectRoots(Set<FileObject> allRoots, FileObject file) {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject project = registry == null ? null : registry.ownerOf(file);
        Set<FileObject> result = new LinkedHashSet<>();
        for (FileObject root : allRoots) {
            if (project != null ? FileUtil.isParentOf(project.getRoot(), root) : FileUtil.isParentOf(root, file)) {
                result.add(root);
            }
        }
        if (result.isEmpty()) {
            ClassPath sourcePath = ClassPath.getClassPath(file, ClassPath.SOURCE);
            FileObject root = sourcePath == null ? null : sourcePath.findOwnerRoot(file);
            if (root != null) {
                result.add(root);
            }
        }
        return result;
    }

    /** The data files in {@code file}'s folder: its package. */
    private static Set<FileObject> packageFiles(FileObject file) {
        Set<FileObject> result = new LinkedHashSet<>();
        FileObject folder = file.getParent();
        if (folder != null) {
            for (FileObject child : folder.getChildren()) {
                if (child.isData()) {
                    result.add(child);
                }
            }
        }
        result.add(file);
        return result;
    }

    /** The dotted path of {@code file}'s folder from its source root, empty when at the root or under none. */
    public String packageNameOf(FileObject file) {
        FileObject root = rootOf(file);
        String relative = root == null || file.getParent() == null ? null : FileUtil.getRelativePath(root, file.getParent());
        return relative == null ? "" : relative.replace('/', '.');
    }

    private static Set<FileObject> openFiles() {
        Set<FileObject> result = new LinkedHashSet<>();
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        if (contentManager == null) {
            return result;
        }
        List<String> projectPaths = new ArrayList<>();
        if (registry != null) {
            for (OpenProject project : registry.getOpenProjects()) {
                projectPaths.add(project.getPath());
            }
        }
        projectPaths.add(null);
        for (String path : projectPaths) {
            for (EditorDocument document : contentManager.documentsOf(path)) {
                if (document.getFileObject() != null) {
                    result.add(document.getFileObject());
                }
            }
        }
        return result;
    }

    public Set<FileObject> roots() {
        return roots;
    }

    /** The only files searched among the roots' - the open ones, for that scope - or {@code null} when all are. */
    public Set<FileObject> files() {
        return files;
    }

    /** Whether {@code file} is among the files this set searches. */
    public boolean accepts(FileObject file) {
        return files == null || files.contains(file);
    }

    /** The source root of {@code file} among {@link #roots()}, or the one its classpath names, or {@code null}. */
    public FileObject rootOf(FileObject file) {
        for (FileObject root : roots) {
            if (root.equals(file) || FileUtil.isParentOf(root, file)) {
                return root;
            }
        }
        ClassPath sourcePath = ClassPath.getClassPath(file, ClassPath.SOURCE);
        return sourcePath == null ? null : sourcePath.findOwnerRoot(file);
    }

    /** The editor text of {@code file}, or {@code null} when it is not open. */
    String openTextOf(FileObject file) {
        return openSources == null ? null : openSources.textOf(file);
    }

    /** The text a query reads for {@code file}: the editor's when open, else the disk's. */
    String textOf(FileObject file) throws IOException {
        String open = openTextOf(file);
        return open != null ? open : file.asText();
    }

    /**
     * The files worth compiling for {@code target}: its own file, and - unless the element is only
     * visible there - every Java file under the roots whose text mentions the element's name; all
     * of them only if {@link #accepts accepted}.
     */
    public List<FileObject> candidates(UsagesTarget target, Cancellation cancellation) {
        LinkedHashSet<FileObject> result = new LinkedHashSet<>();
        if (accepts(target.getFile())) {
            result.add(target.getFile());
        }
        if (target.isFileLocal() || !target.isSearchableByName()) {
            return new ArrayList<>(result);
        }
        String name = target.getSimpleName();
        for (FileObject root : roots) {
            Enumeration<? extends FileObject> children = root.getChildren(true);
            while (children.hasMoreElements()) {
                if (cancellation.isCancelled()) {
                    return new ArrayList<>(result);
                }
                FileObject file = children.nextElement();
                if (file.isData() && "java".equals(file.getExt()) && accepts(file) && !result.contains(file)
                        && mentions(file, name)) {
                    result.add(file);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private boolean mentions(FileObject file, String name) {
        try {
            return textOf(file).contains(name);
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Could not read " + file, ex);
            return false;
        }
    }

    /** A {@link JavaSource} for {@code file} alone, or {@code null} when it has no Java classpath. */
    JavaSource javaSourceOf(FileObject file) throws IOException {
        Compilation compilation = compile(List.of(file));
        return compilation.javaSources().isEmpty() ? null : compilation.javaSources().get(0);
    }

    /**
     * Prepares {@code files} for compilation: one {@link JavaSource} per source root (files of one
     * root share a classpath), each open file replaced by an in-memory copy of its editor text.
     */
    Compilation compile(Collection<FileObject> files) throws IOException {
        Map<FileObject, List<FileObject>> byRoot = new LinkedHashMap<>();
        Map<FileObject, FileObject> originals = new HashMap<>();
        for (FileObject file : files) {
            FileObject compiled = file;
            String open = openTextOf(file);
            if (open != null) {
                compiled = MemorySources.copyOf(file, open);
            }
            originals.put(compiled, file);
            byRoot.computeIfAbsent(rootOf(file), r -> new ArrayList<>()).add(compiled);
        }
        List<JavaSource> javaSources = new ArrayList<>();
        for (Map.Entry<FileObject, List<FileObject>> entry : byRoot.entrySet()) {
            FileObject classpathOwner = entry.getKey() != null ? entry.getKey() : originals.get(entry.getValue().get(0));
            ClasspathInfo classpathInfo = ClasspathInfo.create(classpathOwner);
            javaSources.add(JavaSource.create(classpathInfo, entry.getValue()));
        }
        return new Compilation(javaSources, originals);
    }

    /**
     * A caret offset in the editor's {@code '\n'}-counted text, moved to the text {@code info}
     * compiles - the same text unless the file was read from disk with {@code \r\n} line breaks.
     */
    static int textOffset(CompilationInfo info, int offset) {
        String text = info.getText();
        if (text.indexOf('\r') < 0) {
            return offset;
        }
        int remaining = offset;
        int i = 0;
        while (i < text.length() && remaining > 0) {
            if (text.charAt(i) != '\r') {
                remaining--;
            }
            i++;
        }
        return i;
    }

    /** The offset in the editor's {@code '\n'}-counted text of {@code offset} in the compiled {@code text}. */
    static int editorOffset(CharSequence text, int offset) {
        int carriageReturns = 0;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\r') {
                carriageReturns++;
            }
        }
        return offset - carriageReturns;
    }

    /**
     * The sources of one run, grouped for javac.
     *
     * @param javaSources one per source root
     * @param originals   the on-disk file each compiled file stands for (itself, or the file an
     *                    in-memory copy was made from)
     */
    record Compilation(List<JavaSource> javaSources, Map<FileObject, FileObject> originals) {

        FileObject originalOf(FileObject compiled) {
            return originals.getOrDefault(compiled, compiled);
        }
    }
}

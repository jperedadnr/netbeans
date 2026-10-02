package com.gluonhq.netbeans.nbfx.findusages.query;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import javax.lang.model.element.Element;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.openide.filesystems.FileObject;

/**
 * The search for a Java element: compiles the {@link SourceSet#candidates candidate files} root by
 * root and confirms each occurrence with a {@link UsageScanner}.
 */
final class JavaSearch implements SearchTarget.Search {

    private final UsagesTarget target;
    private final SourceSet sources;
    private final boolean searchComments;

    JavaSearch(UsagesTarget target, SourceSet sources, boolean searchComments) {
        this.target = target;
        this.sources = sources;
        this.searchComments = searchComments;
    }

    @Override
    public boolean targetExists() throws IOException {
        JavaSource javaSource = sources.javaSourceOf(target.getFile());
        if (javaSource == null) {
            return false;
        }
        AtomicBoolean exists = new AtomicBoolean();
        javaSource.runUserActionTask(info -> {
            if (info.toPhase(JavaSource.Phase.RESOLVED).compareTo(JavaSource.Phase.RESOLVED) < 0) {
                return;
            }
            exists.set(target.resolveIn(info, target.getFile()) != null);
        }, true);
        return exists.get();
    }

    @Override
    public void run(Cancellation cancellation, Consumer<List<Usage>> sink, DoubleConsumer progress) throws IOException {
        List<FileObject> candidates = new ArrayList<>(sources.candidates(target, cancellation));
        if (cancellation.isCancelled()) {
            return;
        }
        // The declaring file is always compiled - the element resolves through it - but only
        // scanned when the sources accept it.
        if (!candidates.contains(target.getFile())) {
            candidates.add(target.getFile());
        }
        SourceSet.Compilation compilation = sources.compile(candidates);
        int[] scanned = {0};
        for (JavaSource javaSource : compilation.javaSources()) {
            if (cancellation.isCancelled()) {
                return;
            }
            javaSource.runUserActionTask(info -> {
                if (cancellation.isCancelled()) {
                    return;
                }
                scanUnit(info, compilation, cancellation, sink);
                scanned[0]++;
                progress.accept(candidates.isEmpty() ? 1 : (double) scanned[0] / candidates.size());
            }, true);
        }
    }

    private void scanUnit(CompilationController info, SourceSet.Compilation compilation, Cancellation cancellation,
            Consumer<List<Usage>> sink) throws IOException {
        if (info.toPhase(JavaSource.Phase.RESOLVED).compareTo(JavaSource.Phase.RESOLVED) < 0) {
            return;
        }
        FileObject original = compilation.originalOf(info.getFileObject());
        if (!sources.accepts(original)) {
            return;
        }
        Element element = target.resolveIn(info, original);
        if (element == null) {
            return;
        }
        FileContext fileContext = FileContext.of(original, info.getCompilationUnit(), sources.rootOf(original));
        List<Usage> found = new UsageScanner(info, element, fileContext, searchComments, cancellation).scan();
        if (!found.isEmpty() && !cancellation.isCancelled()) {
            sink.accept(found);
        }
    }
}

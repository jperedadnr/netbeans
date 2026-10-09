package com.gluonhq.netbeans.nbfx.project.ui.maven;

import java.io.InputStream;
import java.io.OutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Minimal read/write access to the top-level elements of a Maven {@code pom.xml} that the General
 * customizer edits (groupId, artifactId, version, name, packaging).
 *
 * @since 1.0
 */
final class MavenProjectFiles {

    private static final String POM_NS = "http://maven.apache.org/POM/4.0.0";

    private MavenProjectFiles() {
    }

    /** The value of the POM {@code <build>} entry {@code tag}, or {@code null}. */
    static String pomBuildEntry(FileObject dir, String tag) {
        Element project = projectElement(dir);
        if (project == null) {
            return null;
        }
        Element build = child(project, "build");
        if (build == null) {
            return null;
        }
        Element element = child(build, tag);
        return element == null ? null : element.getTextContent().trim();
    }

    /** Sets the POM {@code <build>} entry {@code tag}, adding {@code <build>} when absent. */
    static void setPomBuildEntry(FileObject dir, String tag, String value) {
        FileObject file = dir.getFileObject("pom.xml");
        if (file == null || !file.isData()) {
            return;
        }
        try (InputStream in = file.getInputStream()) {
            Document document = newDocumentBuilder().parse(in);
            Element project = document.getDocumentElement();
            Element build = child(project, "build");
            if (build == null) {
                build = document.createElementNS(POM_NS, "build");
                project.appendChild(build);
            }
            Element element = child(build, tag);
            if (element == null) {
                element = document.createElementNS(POM_NS, tag);
                build.appendChild(element);
            }
            element.setTextContent(value);
            write(file, document);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /** The text of the top-level {@code tag} in {@code pom.xml}, or {@code null}. */
    static String pomEntry(FileObject dir, String tag) {
        Element project = projectElement(dir);
        if (project == null) {
            return null;
        }
        Element element = child(project, tag);
        return element == null ? null : element.getTextContent().trim();
    }

    /** The value of the POM {@code <properties>} entry {@code name}, or {@code null}. */
    static String pomProperty(FileObject dir, String name) {
        Element project = projectElement(dir);
        if (project == null) {
            return null;
        }
        Element properties = child(project, "properties");
        if (properties == null) {
            return null;
        }
        Element element = child(properties, name);
        return element == null ? null : element.getTextContent().trim();
    }

    /** Sets the POM {@code <properties>} entry {@code name}, adding it when absent. */
    static void setPomProperty(FileObject dir, String name, String value) {
        FileObject file = dir.getFileObject("pom.xml");
        if (file == null || !file.isData()) {
            return;
        }
        try (InputStream in = file.getInputStream()) {
            Document document = newDocumentBuilder().parse(in);
            Element project = document.getDocumentElement();
            Element properties = child(project, "properties");
            if (properties == null) {
                properties = document.createElementNS(POM_NS, "properties");
                project.appendChild(properties);
            }
            Element element = child(properties, name);
            if (element == null) {
                element = document.createElementNS(POM_NS, name);
                properties.appendChild(element);
            }
            element.setTextContent(value);
            write(file, document);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /** Sets the text of the top-level {@code tag} in {@code pom.xml}, adding it when absent. */
    static void setPomEntry(FileObject dir, String tag, String value) {
        FileObject file = dir.getFileObject("pom.xml");
        if (file == null || !file.isData()) {
            return;
        }
        try (InputStream in = file.getInputStream()) {
            Document document = newDocumentBuilder().parse(in);
            Element project = document.getDocumentElement();
            Element element = child(project, tag);
            if (element == null) {
                element = document.createElementNS(POM_NS, tag);
                project.appendChild(element);
            }
            element.setTextContent(value);
            write(file, document);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private static Element projectElement(FileObject dir) {
        FileObject file = dir.getFileObject("pom.xml");
        if (file == null || !file.isData()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            return newDocumentBuilder().parse(in).getDocumentElement();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    private static Element child(Element parent, String localName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }

    private static void write(FileObject file, Document document) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        FileLock lock = null;
        try {
            lock = file.lock();
            try (OutputStream out = file.getOutputStream(lock)) {
                transformer.transform(new DOMSource(document), new StreamResult(out));
            }
        } finally {
            if (lock != null) {
                lock.releaseLock();
            }
        }
    }
}

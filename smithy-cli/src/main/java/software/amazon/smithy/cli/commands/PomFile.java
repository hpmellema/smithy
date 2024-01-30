package software.amazon.smithy.cli.commands;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.SmithyBuildException;
import software.amazon.smithy.cli.dependencies.ResolvedArtifact;

final class PomFile {
    private final Document doc;
    private final Element project;
    private final String artifactId;
    private final String version;

    public PomFile(String artifactId, String groupId, String version) {
        this.artifactId = artifactId;
        this.version = version;
        doc = createBaseDocument();

        // Add root project element
        project = doc.createElement("project");
        doc.appendChild(project);

        Attr xmlns = doc.createAttribute("xmlns");
        xmlns.setValue("http://maven.apache.org/POM/4.0.0");
        project.setAttributeNode(xmlns);
        Attr xmlnsXsi = doc.createAttribute("xmlns:xsi");
        xmlnsXsi.setValue("http://www.w3.org/2001/XMLSchema-instance");
        project.setAttributeNode(xmlnsXsi);
        Attr xsiSchemaLocation = doc.createAttribute("xsi:schemaLocation");
        xsiSchemaLocation.setValue("http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd");
        project.setAttributeNode(xsiSchemaLocation);

        // model version
        Element modelVersion = doc.createElement("modelVersion");
        modelVersion.appendChild(doc.createTextNode("4.0.0"));
        project.appendChild(modelVersion);

        // artifactId
        Element artifactIdNode = doc.createElement("artifactId");
        artifactIdNode.appendChild(doc.createTextNode(artifactId));
        project.appendChild(artifactIdNode);

        Element groupIdNode = doc.createElement("groupId");
        groupIdNode.appendChild(doc.createTextNode(groupId));
        project.appendChild(groupIdNode);

        // version
        Element versionNode = doc.createElement("version");
        versionNode.appendChild(doc.createTextNode(version));
        project.appendChild(versionNode);
    }

    private static Document createBaseDocument() {
        try {
            DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            return dBuilder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new SmithyBuildException(e);
        }
    }

    public void addDependencies(List<ResolvedArtifact> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        Element scope = doc.createElement("scope");
        scope.appendChild(doc.createTextNode("runtime"));
        Element deps = doc.createElement("dependencies");
        project.appendChild(deps);

        for (ResolvedArtifact dep : dependencies) {
            Element dependency = doc.createElement("dependency");

            Element depGroup = doc.createElement("groupId");
            depGroup.appendChild(doc.createTextNode(dep.getGroupId()));
            dependency.appendChild(depGroup);

            Element depId = doc.createElement("artifactId");
            depId.appendChild(doc.createTextNode(dep.getArtifactId()));
            dependency.appendChild(depId);

            Element depVer = doc.createElement("version");
            depVer.appendChild(doc.createTextNode(dep.getVersion()));
            dependency.appendChild(depVer);

            dependency.appendChild(scope);
            deps.appendChild(dependency);
        }
    }

    // TODO: docs
    public void write(FileManifest manifest) {
        // Poms are usually named ${artifactId}-${version}.pom by both Gradle and
        // maven. We follow the same convention here.
        Path outputFilePath = manifest.addFile(Paths.get(artifactId + "-" + version + ".pom"));
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            // Improves readability of generated POM
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(Files.newOutputStream(outputFilePath));
            transformer.transform(source, result);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

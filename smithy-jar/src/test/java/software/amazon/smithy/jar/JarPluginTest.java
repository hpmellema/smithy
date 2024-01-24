package software.amazon.smithy.jar;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.utils.ListUtils;

public class JarPluginTest {
    private Path tempDir;

    @BeforeEach
    void init() throws IOException {
        tempDir = Files.createTempDirectory("jar-test");
    }

    @Test
    public void copiesFilesForSourceProjection() throws URISyntaxException, IOException {
        Model model = Model.assembler()
                .addImport(getClass().getResource("sources/a.smithy"))
                .addImport(getClass().getResource("sources/b.smithy"))
                .addImport(getClass().getResource("sources/c/c.json"))
                .addImport(getClass().getResource("notsources/d.smithy"))
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest(tempDir);
        PluginContext context = PluginContext.builder()
                .settings(getDummySettings())
                .fileManifest(manifest)
                .model(model)
                .originalModel(model)
                .sources(ListUtils.of(Paths.get(getClass().getResource("sources/a.smithy").toURI()).getParent()))
                .build();
        new JarPlugin().execute(context);

        TestUtils.assertJarContains(tempDir.toString(), "source.jar", "META-INF/smithy/manifest",
                "META-INF/smithy/a.smithy",
                "META-INF/smithy/b.smithy",
                "META-INF/smithy/c/c.json",
                "META-INF/maven/com.example.smithy/my-app/pom.properties",
                "META-INF/maven/com.example.smithy/my-app/pom.xml");

        try (JarFile jar = new JarFile(new File(tempDir.toString(), "source.jar"))) {
            Manifest jarManifest = jar.getManifest();
            assertThat(jarManifest.getMainAttributes().getValue("Created-With"),
                    equalTo("Smithy-Jar-Plugin (" + TestUtils.getVersion() + ")"));
            assertThat(jarManifest.getMainAttributes().getValue("Build-Timestamp"), notNullValue());
        }
    }


    @Test
    public void addsTags() throws URISyntaxException, IOException {
        Model model = Model.assembler()
                .addImport(getClass().getResource("sources/a.smithy"))
                .addImport(getClass().getResource("sources/b.smithy"))
                .addImport(getClass().getResource("sources/c/c.json"))
                .addImport(getClass().getResource("notsources/d.smithy"))
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest(tempDir);

        ObjectNode settings = getDummySettings().toBuilder()
                .withMember("tags", arrayNodeFromStringList(ListUtils.of("a", "b")))
                .build();
        PluginContext context = PluginContext.builder()
                .settings(settings)
                .fileManifest(manifest)
                .model(model)
                .originalModel(model)
                .sources(ListUtils.of(Paths.get(getClass().getResource("sources/a.smithy").toURI()).getParent()))
                .build();
        new JarPlugin().execute(context);

        TestUtils.assertJarContains(tempDir.toString(), "source.jar", "META-INF/smithy/manifest",
                "META-INF/smithy/a.smithy",
                "META-INF/smithy/b.smithy",
                "META-INF/smithy/c/c.json");

        try (JarFile jar = new JarFile(new File(tempDir.toString(), "source.jar"))) {
            Manifest jarManifest = jar.getManifest();
            assertThat(jarManifest.getMainAttributes().getValue("Created-With"),
                    equalTo("Smithy-Jar-Plugin (" + TestUtils.getVersion() + ")"));
            assertThat(jarManifest.getMainAttributes().getValue("Build-Timestamp"), notNullValue());
            assertThat(jarManifest.getMainAttributes().getValue("Smithy-Tags"), equalTo("a,b"));
        }
    }


    @Test
    public void addsManifestHeaders() throws URISyntaxException, IOException {
        Model model = Model.assembler()
                .addImport(getClass().getResource("sources/a.smithy"))
                .addImport(getClass().getResource("sources/b.smithy"))
                .addImport(getClass().getResource("sources/c/c.json"))
                .addImport(getClass().getResource("notsources/d.smithy"))
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest(tempDir);

        ObjectNode settings = getDummySettings().toBuilder()
                .withMember("manifestHeaders", ObjectNode.builder()
                        .withMember("Test-Header-1", "foo")
                        .withMember("Test-Header-2", "bar")
                        .build())
                .build();
        PluginContext context = PluginContext.builder()
                .settings(settings)
                .fileManifest(manifest)
                .model(model)
                .originalModel(model)
                .sources(ListUtils.of(Paths.get(getClass().getResource("sources/a.smithy").toURI()).getParent()))
                .build();
        new JarPlugin().execute(context);

        TestUtils.assertJarContains(tempDir.toString(), "source.jar", "META-INF/smithy/manifest",
                "META-INF/smithy/a.smithy",
                "META-INF/smithy/b.smithy",
                "META-INF/smithy/c/c.json");

        try (JarFile jar = new JarFile(new File(tempDir.toString(), "source.jar"))) {
            Manifest jarManifest = jar.getManifest();
            assertThat(jarManifest.getMainAttributes().getValue("Created-With"),
                    equalTo("Smithy-Jar-Plugin (" + TestUtils.getVersion() + ")"));
            assertThat(jarManifest.getMainAttributes().getValue("Build-Timestamp"), notNullValue());
            assertThat(jarManifest.getMainAttributes().getValue("Test-Header-1"), equalTo("foo"));
            assertThat(jarManifest.getMainAttributes().getValue("Test-Header-2"), equalTo("bar"));
        }
    }

    private static ObjectNode getDummySettings() {
        return ObjectNode.builder()
                .withMember("artifactId", "my-app")
                .withMember("groupId", "com.example.smithy")
                .withMember("version", "1.0.1")
                .withMember("requires",
                        arrayNodeFromStringList(ListUtils.of("software.amazon.smithy:smithy-aws-traits:1.43.0")))
                .build();
    }

    private static ArrayNode arrayNodeFromStringList(List<String> strings) {
        return strings.stream()
                .map(StringNode::from)
                .collect(ArrayNode.collect());
    }
}

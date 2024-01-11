package software.amazon.smithy.build.plugins;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.utils.ListUtils;

public class JarPluginTest {
    @Test
    public void copiesFilesForSourceProjection() throws URISyntaxException {
        Model model = Model.assembler()
                .addImport(getClass().getResource("sources/a.smithy"))
                .addImport(getClass().getResource("sources/b.smithy"))
                .addImport(getClass().getResource("sources/c/c.json"))
                .addImport(getClass().getResource("notsources/d.smithy"))
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        PluginContext context = PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .originalModel(model)
                .sources(ListUtils.of(Paths.get(getClass().getResource("sources/a.smithy").toURI()).getParent()))
                .build();
        new JarPlugin().execute(context);

        //String manifestString = manifest.getFileString("source.jar").get();
        // Normalize for Windows.
       // manifestString = manifestString.replace("\\", "/");

//        assertThat(manifestString, containsString("a.smithy\n"));
//        assertThat(manifestString, containsString("b.smithy\n"));
//        assertThat(manifestString, containsString("c/c.json\n"));
//        assertThat(manifestString, not(containsString("d.json")));
//        assertThat(manifest.getFileString("a.smithy").get(), containsString("AString"));
//        assertThat(manifest.getFileString("b.smithy").get(), containsString("BString"));
//        assertThat(manifest.getFileString("c/c.json").get(), containsString("CString"));
    }
}

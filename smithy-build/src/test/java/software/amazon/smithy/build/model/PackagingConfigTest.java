package software.amazon.smithy.build.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

public class PackagingConfigTest {
    @Test
    public void hasCorrectDefaultsBuiltInToThePojo() {
        PackagingConfig config = PackagingConfig.builder().build();

        assertThat(config.getArtifactId().isPresent(), is(false));
        assertThat(config.getVersion().isPresent(), is(false));
        assertThat(config.getGroupId().isPresent(), is(false));
        assertThat(config.getTags(), emptyIterable());
        assertThat(config.getInclude(), emptyIterable());
        assertThat(config.getPomData().isPresent(), is(false));
        assertThat(config.getIncludeAt(), hasEntry("sources", "META-INF/smithy"));
        assertThat(config.getManifestHeaders(), anEmptyMap());
    }

    @Test
    public void loadsMinimalConfig() {
        PackagingConfig config = PackagingConfig.fromNode(Node.objectNode()
                .withMember("artifactId", "my-artifact")
                .withMember("groupId", "com.example.smithy")
                .withMember("version", "1.0.1")
        );

        assertThat(config.getArtifactId().get(), is("my-artifact"));
        assertThat(config.getGroupId().get(), is("com.example.smithy"));
        assertThat(config.getVersion().get(), is("1.0.1"));
        assertThat(config.getTags(), emptyIterable());
        assertThat(config.getInclude(), emptyIterable());
        assertThat(config.getPomData().isPresent(), is(false));
        assertThat(config.getIncludeAt(), hasEntry("sources", "META-INF/smithy"));
        assertThat(config.getManifestHeaders(), anEmptyMap());
    }

    @Test
    public void overridesDefault() {
        PackagingConfig config = PackagingConfig.fromNode(Node.objectNode()
                .withMember("artifactId", "my-artifact")
                .withMember("groupId", "com.example.smithy")
                .withMember("version", "1.0.1")
                .withMember("include-at", ObjectNode.builder()
                        .withMember("sources", "other/location")
                        .build())
        );

        assertThat(config.getArtifactId().get(), is("my-artifact"));
        assertThat(config.getGroupId().get(), is("com.example.smithy"));
        assertThat(config.getVersion().get(), is("1.0.1"));
        assertThat(config.getTags(), emptyIterable());
        assertThat(config.getInclude(), emptyIterable());
        assertThat(config.getPomData().isPresent(), is(false));
        assertThat(config.getIncludeAt(), hasEntry("sources", "other/location"));
        assertThat(config.getManifestHeaders(), anEmptyMap());
    }

    @Test
    public void convertToBuilder() {
        PackagingConfig config1 = PackagingConfig.fromNode(Node.objectNode()
                .withMember("artifactId", "my-artifact")
                .withMember("groupId", "com.example.smithy")
                .withMember("version", "1.0.1")
        );
        PackagingConfig config2 = config1.toBuilder().build();

        assertThat(config1, equalTo(config1));
        assertThat(config1, equalTo(config2));
    }

    @Test
    public void mergesConfigs() {
        PackagingConfig config1 = PackagingConfig.fromNode(Node.objectNode()
                .withMember("artifactId", "my-artifact")
                .withMember("groupId", "com.example.smithy")
                .withMember("tags", ArrayNode.fromStrings("a", "b"))
        );
        PackagingConfig config2 = PackagingConfig.fromNode(Node.objectNode()
                .withMember("artifactId", "my-other-artifact")
                .withMember("version", "1.0.1")
                .withMember("tags", ArrayNode.fromStrings("c", "d"))
        );
        PackagingConfig merged = config2.merge(config1);
        assertThat(merged.getArtifactId().get(), is("my-artifact"));
        assertThat(merged.getGroupId().get(), is("com.example.smithy"));
        assertThat(merged.getVersion().get(), is("1.0.1"));
        assertThat(merged.getTags(), contains("c", "d", "a", "b"));
    }
}

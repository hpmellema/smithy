package software.amazon.smithy.build.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.node.Node;

public class PackagingConfigTest {
    @Test
    public void hasNoDefaultsBuiltInToThePojo() {
        PackagingConfig config = PackagingConfig.builder().build();

        assertThat(config.getArtifactId(), nullValue());
        assertThat(config.getVersion(), nullValue());
        assertThat(config.getGroupId(), nullValue());
        assertThat(config.getTags(), emptyIterable());
        assertThat(config.getInclude(), emptyIterable());
        assertThat(config.getPomData().isPresent(), is(false));
        assertThat(config.getIncludeAt(), anEmptyMap());
        assertThat(config.getManifestHeaders(), anEmptyMap());
    }

    @Test
    public void loadsMinimalConfig() {
        PackagingConfig config = PackagingConfig.fromNode(Node.objectNode()
                .withMember("artifactId", "my-artifact")
                .withMember("groupId", "com.example.smithy")
                .withMember("version", "1.0.1")
        );

        assertThat(config.getArtifactId(), is("my-artifact"));
        assertThat(config.getGroupId(), is("com.example.smithy"));
        assertThat(config.getVersion(), is("1.0.1"));
        assertThat(config.getTags(), emptyIterable());
        assertThat(config.getInclude(), emptyIterable());
        assertThat(config.getPomData().isPresent(), is(false));
        assertThat(config.getIncludeAt(), anEmptyMap());
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
}

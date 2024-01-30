/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.build.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.utils.BuilderRef;
import software.amazon.smithy.utils.ListUtils;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.ToSmithyBuilder;

public final class PackagingConfig implements ToSmithyBuilder<PackagingConfig> {
    private static final List<String> PROPERTIES = ListUtils.of("artifactId", "groupId", "version",
            "tags", "manifestHeaders", "pomData");
    private static final String MANIFEST_HEADERS = "manifestHeaders";
    private final String artifactId;
    private final String groupId;
    private final String version;
    private final Set<String> tags;
    private final Map<String, String> manifestHeaders;
    private final ObjectNode pomData;

    private PackagingConfig(Builder builder) {
        this.artifactId = builder.artifactId;
        this.groupId = builder.groupId;
        this.version = builder.version;
        this.tags = builder.tags.copy();
        this.manifestHeaders = builder.manifestHeaders.copy();
        this.pomData = builder.pomData;
    }

    public static PackagingConfig fromNode(Node node) {
        PackagingConfig.Builder builder = builder();
        ObjectNode objectNode = node.expectObjectNode();
        objectNode.warnIfAdditionalProperties(PROPERTIES)
                .getStringMember("artifactId", builder::artifactId)
                .getStringMember("groupId", builder::groupId)
                .getStringMember("version", builder::version)
                .getArrayMember("tags", StringNode::getValue, builder::tags)
                .getObjectMember("pomData", builder::pomData);

        if (objectNode.containsMember(MANIFEST_HEADERS)) {
            for (Map.Entry<String, Node> entry : objectNode.expectObjectMember(MANIFEST_HEADERS)
                    .getStringMap().entrySet()
            ) {
                builder.putManifestHeader(entry.getKey(), entry.getValue().expectStringNode().getValue());
            }
        }

        return builder.build();
    }

    public PackagingConfig merge(PackagingConfig other) {
        Builder builder = toBuilder();

        other.getArtifactId().ifPresent(builder::artifactId);
        other.getGroupId().ifPresent(builder::groupId);
        other.getVersion().ifPresent(builder::version);
        builder.tags.get().addAll(other.getTags());
        builder.manifestHeaders.get().putAll(other.getManifestHeaders());

        if (other.getPomData().isPresent()) {
            builder.pomData = builder.pomData.toBuilder()
                    .merge(other.getPomData().get())
                    .build();
        }

        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder toBuilder() {
        return builder().artifactId(artifactId)
                .groupId(groupId)
                .version(version)
                .tags(tags)
                .pomData(pomData)
                .manifestHeaders(manifestHeaders);
    }

    public Optional<String> getArtifactId() {
        return Optional.ofNullable(artifactId);
    }

    public Optional<String> getGroupId() {
        return Optional.ofNullable(groupId);
    }

    public Optional<String> getVersion() {
        return Optional.ofNullable(version);
    }

    public Set<String> getTags() {
        return tags;
    }

    public Map<String, String> getManifestHeaders() {
        return manifestHeaders;
    }

    public Optional<ObjectNode> getPomData() {
        return Optional.ofNullable(pomData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PackagingConfig that = (PackagingConfig) o;
        return Objects.equals(artifactId, that.artifactId)
                && Objects.equals(groupId, that.groupId)
                && Objects.equals(version, that.version)
                && Objects.equals(tags, that.tags)
                && Objects.equals(manifestHeaders, that.manifestHeaders)
                && Objects.equals(pomData, that.pomData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, groupId, version, tags, manifestHeaders, pomData);
    }


    public static final class Builder implements SmithyBuilder<PackagingConfig> {
        private String artifactId;
        private String groupId;
        private String version;

        private final BuilderRef<Set<String>> tags = BuilderRef.forOrderedSet();
        private final BuilderRef<Map<String, String>> manifestHeaders = BuilderRef.forOrderedMap();

        private ObjectNode pomData;

        private Builder() {}

        @Override
        public PackagingConfig build() {
            return new PackagingConfig(this);
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder tags(Collection<String> tags) {
            this.tags.clear();
            this.tags.get().addAll(tags);
            return this;
        }

        public Builder manifestHeaders(Map<String, String> manifestHeaders) {
            this.manifestHeaders.clear();
            this.manifestHeaders.get().putAll(manifestHeaders);
            return this;
        }

        public Builder putManifestHeader(String key, String value) {
            this.manifestHeaders.get().put(key, value);
            return this;
        }

        public Builder pomData(ObjectNode pomData) {
            this.pomData = pomData;
            return this;
        }
    }
}

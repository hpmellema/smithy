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
            "include", "include-at", "tags", "manifest-headers", "pom-data");
    private static final String INCLUDE_AT = "include-at";
    private static final String MANIFEST_HEADERS = "manifest-headers";
    private final String artifactId;
    private final String groupId;
    private final String version;
    private final Set<String> include;
    private final Map<String, String> includeAt;
    private final Set<String> tags;
    private final Map<String, String> manifestHeaders;
    private final ObjectNode pomData;

    private PackagingConfig(Builder builder) {
        this.artifactId = builder.artifactId;
        this.groupId = builder.groupId;
        this.version = builder.version;
        this.include = builder.include.copy();
        this.tags = builder.tags.copy();
        this.includeAt = builder.includeAt.copy();
        this.manifestHeaders = builder.manifestHeaders.copy();
        this.pomData = builder.pomData;
    }

    public static PackagingConfig fromNode(Node node) {
        PackagingConfig.Builder builder = builder();
        ObjectNode objectNode = node.expectObjectNode();
        objectNode.warnIfAdditionalProperties(PROPERTIES)
                .expectStringMember("artifactId", builder::artifactId)
                .expectStringMember("groupId", builder::groupId)
                .expectStringMember("version", builder::version)
                .getArrayMember("include", StringNode::getValue, builder::include)
                .getArrayMember("tags", StringNode::getValue, builder::tags)
                .getObjectMember("pom-data", builder::pomData);

        if (objectNode.containsMember(INCLUDE_AT)) {
            for (Map.Entry<String, Node> entry : objectNode.expectObjectMember(INCLUDE_AT).getStringMap().entrySet()) {
                builder.putIncludeAt(entry.getKey(), entry.getValue().expectStringNode().getValue());
            }
        }

        if (objectNode.containsMember(MANIFEST_HEADERS)) {
            for (Map.Entry<String, Node> entry : objectNode.expectObjectMember(MANIFEST_HEADERS)
                    .getStringMap().entrySet()
            ) {
                builder.putIncludeAt(entry.getKey(), entry.getValue().expectStringNode().getValue());
            }
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
                .include(include)
                .includeAt(includeAt)
                .tags(tags)
                .pomData(pomData)
                .manifestHeaders(manifestHeaders);
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getVersion() {
        return version;
    }

    public Set<String> getInclude() {
        return include;
    }

    public Map<String, String> getIncludeAt() {
        return includeAt;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackagingConfig that = (PackagingConfig) o;
        return Objects.equals(artifactId, that.artifactId)
                && Objects.equals(groupId, that.groupId)
                && Objects.equals(version, that.version)
                && Objects.equals(include, that.include)
                && Objects.equals(includeAt, that.includeAt)
                && Objects.equals(tags, that.tags)
                && Objects.equals(manifestHeaders, that.manifestHeaders)
                && Objects.equals(pomData, that.pomData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, groupId, version, include, includeAt, tags, manifestHeaders, pomData);
    }


    public static final class Builder implements SmithyBuilder<PackagingConfig> {
        private String artifactId;
        private String groupId;
        private String version;

        private final BuilderRef<Set<String>> include = BuilderRef.forOrderedSet();
        private final BuilderRef<Map<String, String>> includeAt = BuilderRef.forOrderedMap();
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

        public Builder include(Collection<String> include) {
            this.include.clear();
            this.include.get().addAll(include);
            return this;
        }

        public Builder putIncludeAt(String key, String value) {
            this.includeAt.get().put(key, value);
            return this;
        }

        public Builder includeAt(Map<String, String> includeAt) {
            this.includeAt.clear();
            this.includeAt.get().putAll(includeAt);
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

/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.build.SmithyBuildException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.utils.BuilderRef;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.ToSmithyBuilder;

/**
 * ProjectionConfig stored in a {@link SmithyBuildConfig}.
 */
public final class ProjectionConfig implements ToSmithyBuilder<ProjectionConfig> {
    private final boolean isAbstract;
    private final List<String> imports;
    private final List<TransformConfig> transforms;
    private final Map<String, ObjectNode> plugins;

    private final PackagingConfig packaging;

    private ProjectionConfig(Builder builder) {
        this.imports = builder.imports.copy();
        this.transforms = builder.transforms.copy();
        this.isAbstract = builder.isAbstract;
        this.plugins = builder.plugins.copy();
        this.packaging = builder.packaging;

        if (isAbstract && (!plugins.isEmpty() || !imports.isEmpty())) {
            throw new SmithyBuildException("Abstract projections must not define plugins or imports");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder toBuilder() {
        return builder()
                .imports(imports)
                .plugins(plugins)
                .packaging(packaging)
                .transforms(transforms)
                .setAbstract(isAbstract);
    }

    public static ProjectionConfig fromNode(Node node) {
        return fromNode(node, SmithyBuildUtils.getBasePathFromSourceLocation(node));
    }

    static ProjectionConfig fromNode(Node node, Path basePath) {
        Builder builder = ProjectionConfig.builder();
        node.expectObjectNode()
                .getBooleanMember("abstract", builder::setAbstract)
                .getArrayMember("imports", s -> SmithyBuildUtils.resolveImportPath(basePath, s),
                                builder::imports)
                .getArrayMember("transforms", TransformConfig::fromNode, builder::transforms)
                .getObjectMember("plugins", plugins -> {
                    for (Map.Entry<String, Node> entry : plugins.getStringMap().entrySet()) {
                        builder.plugins.get().put(entry.getKey(), entry.getValue().expectObjectNode());
                    }
                })
                .getObjectMember("packaging", PackagingConfig::fromNode);
        return builder.build();
    }

    /**
     * @return Gets the immutable transforms in the projection.
     */
    public List<TransformConfig> getTransforms() {
        return transforms;
    }

    /**
     * @return Gets the immutable plugins of the projection.
     */
    public Map<String, ObjectNode> getPlugins() {
        return plugins;
    }

    /**
     * @return Returns true if the projection is abstract.
     */
    public boolean isAbstract() {
        return isAbstract;
    }

    /**
     * Gets the imports configured for the projection.
     *
     * @return Returns the projection-specific imports.
     */
    public List<String> getImports() {
        return imports;
    }

    /**
     * Builds a {@link ProjectionConfig}.
     */
    public static final class Builder implements SmithyBuilder<ProjectionConfig> {
        private boolean isAbstract;
        private final BuilderRef<List<String>> imports = BuilderRef.forList();
        private final BuilderRef<List<TransformConfig>> transforms = BuilderRef.forList();
        private final BuilderRef<Map<String, ObjectNode>> plugins = BuilderRef.forOrderedMap();
        private PackagingConfig packaging;

        private Builder() {}

        /**
         * Builds the projection.
         *
         * @return Returns the created projection.
         */
        public ProjectionConfig build() {
            return new ProjectionConfig(this);
        }

        /**
         * Sets the {@code abstract} property of the projection.
         *
         * <p>Abstract projections do not directly create any artifacts.
         *
         * @param isAbstract Set to true to mark as abstract.
         * @return Returns the builder.
         */
        public Builder setAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
            return this;
        }

        /**
         * Replaces the imports of the projection.
         *
         * @param imports Imports to set.
         * @return Returns the builder.
         */
        public Builder imports(Collection<String> imports) {
            this.imports.clear();
            this.imports.get().addAll(imports);
            return this;
        }

        /**
         * Replaces the transforms of the projection.
         *
         * @param transforms Transform to set.
         * @return Returns the builder.
         */
        public Builder transforms(Collection<TransformConfig> transforms) {
            this.transforms.clear();
            this.transforms.get().addAll(transforms);
            return this;
        }

        /**
         * Replaces the plugins of the projection.
         *
         * @param plugins Map of plugin name to plugin settings.
         * @return Returns the builder.
         */
        public Builder plugins(Map<String, ObjectNode> plugins) {
            this.plugins.clear();
            this.plugins.get().putAll(plugins);
            return this;
        }

        /**
         * Replaces the packaging configuration of the projection.
         *
         * @param packaging packaging configuration settings.
         * @return Returns the builder.
         */
        public Builder packaging(PackagingConfig packaging) {
            this.packaging = packaging;
            return this;
        }
    }
}

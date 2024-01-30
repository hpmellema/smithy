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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.utils.BuilderRef;
import software.amazon.smithy.utils.ListUtils;
import software.amazon.smithy.utils.SetUtils;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.ToSmithyBuilder;

public final class MavenConfig implements ToSmithyBuilder<MavenConfig> {
    private final Set<String> dependencies;
    private final Set<String> buildPlugins;
    private final Set<MavenRepository> repositories;

    private MavenConfig(Builder builder) {
        this.dependencies = builder.dependencies.copy();
        this.buildPlugins = builder.buildPlugins.copy();
        this.repositories =  builder.repositories.copy();
    }

    public static MavenConfig fromNode(Node node) {
        MavenConfig.Builder builder = builder();
        node.expectObjectNode()
                .warnIfAdditionalProperties(ListUtils.of("dependencies", "build-plugins", "repositories"))
                .getArrayMember("dependencies", StringNode::getValue, builder::dependencies)
                .getArrayMember("build-plugins", StringNode::getValue, builder::buildPlugins)
                .getArrayMember("repositories", MavenRepository::fromNode, builder::repositories);
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the repositories.
     *
     * @return Returns the repositories in an insertion ordered set.
     */
    public Set<MavenRepository> getRepositories() {
        return repositories;
    }

    /**
     * Gets all dependencies.
     *
     * @return Returns the dependencies in an insertion ordered set.
     */
    public Set<String> getAllDependencies() {
        Set<String> mergedDeps = new LinkedHashSet<>(dependencies);
        mergedDeps.addAll(buildPlugins);
        return mergedDeps;
    }

    /**
     * Gets the dependencies.
     *
     * @return Returns the dependencies in an insertion ordered set.
     */
    public Set<String> getDependencies() {
        return dependencies;
    }

    /**
     * Gets the build plugin dependencies.
     *
     * @return Returns the build plugins in an insertion ordered set.
     */
    public Set<String> getBuildPlugins() {
        return buildPlugins;
    }

    public MavenConfig merge(MavenConfig other) {
        MavenConfig.Builder builder = toBuilder();
        builder.dependencies.get().addAll(other.getDependencies());
        builder.buildPlugins.get().addAll(other.getBuildPlugins());
        if (other.repositories != null) {
            builder.repositories.get().addAll(other.repositories);
        }

        return builder.build();
    }

    @Override
    public Builder toBuilder() {
        return builder().repositories(repositories)
                .buildPlugins(buildPlugins)
                .dependencies(dependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dependencies, buildPlugins, repositories);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof MavenConfig)) {
            return false;
        }

        MavenConfig other = (MavenConfig) obj;
        return dependencies.equals(other.dependencies)
                && Objects.equals(buildPlugins, other.buildPlugins)
                && Objects.equals(repositories, other.repositories);
    }

    public static final class Builder implements SmithyBuilder<MavenConfig> {
        private final BuilderRef<Set<String>> dependencies = BuilderRef.forOrderedSet();
        private final BuilderRef<Set<String>> buildPlugins = BuilderRef.forOrderedSet();
        private final BuilderRef<Set<MavenRepository>> repositories = BuilderRef.forOrderedSet();

        private Builder() {}

        @Override
        public MavenConfig build() {
            return new MavenConfig(this);
        }

        public Builder dependencies(Collection<String> dependencies) {
            this.dependencies.clear();
            this.dependencies.get().addAll(dependencies);
            return this;
        }

        public Builder buildPlugins(Collection<String> buildPlugins) {
            this.buildPlugins.clear();
            this.buildPlugins.get().addAll(buildPlugins);
            return this;
        }

        public Builder repositories(Collection<MavenRepository> repositories) {
            this.repositories.clear();
            if (repositories != null) {
                this.repositories.get().addAll(repositories);
            }
            return this;
        }
    }
}

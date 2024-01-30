/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.cli.commands;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import software.amazon.smithy.build.model.MavenConfig;
import software.amazon.smithy.build.model.MavenRepository;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.cli.CliError;
import software.amazon.smithy.cli.EnvironmentVariable;
import software.amazon.smithy.cli.SmithyCli;
import software.amazon.smithy.cli.dependencies.DependencyResolver;
import software.amazon.smithy.cli.dependencies.FileCacheResolver;
import software.amazon.smithy.cli.dependencies.FilterCliVersionResolver;
import software.amazon.smithy.cli.dependencies.ResolvedArtifact;

final class ConfigurationUtils {
    private static final int FNV_OFFSET_BIAS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x1000193;
    private static final Logger LOGGER = Logger.getLogger(ConfigurationUtils.class.getName());
    private static final MavenRepository CENTRAL = MavenRepository.builder()
            .url("https://repo.maven.apache.org/maven2")
            .build();

    private ConfigurationUtils() {
        // Utility Class should not be instantiated
    }

    /**
     * Gets the list of Maven Repos configured via smithy-build config and environment variables.
     * <p>
     * Environment variables take precedence over config file entries. If no repositories are
     * defined in either environment variables or config file entries, then the returned set
     * defaults to a singleton set of just the Maven Central repo.
     *
     * @param config Smithy build config to check for maven repository values.
     */
    public static Set<MavenRepository> getConfiguredMavenRepos(SmithyBuildConfig config) {
        Set<MavenRepository> repositories = new LinkedHashSet<>();

        String envRepos = EnvironmentVariable.SMITHY_MAVEN_REPOS.get();
        if (envRepos != null) {
            for (String repo : envRepos.split("\\|")) {
                repositories.add(MavenRepository.builder().url(repo.trim()).build());
            }
        }

        Set<MavenRepository> configuredRepos = config.getMaven()
                .map(MavenConfig::getRepositories)
                .orElse(Collections.emptySet());

        if (!configuredRepos.isEmpty()) {
            repositories.addAll(configuredRepos);
        } else if (envRepos == null) {
            LOGGER.finest(() -> String.format("maven.repositories is not defined in `smithy-build.json` and the %s "
                            + "environment variable is not set. Defaulting to Maven Central.",
                    EnvironmentVariable.SMITHY_MAVEN_REPOS));
            repositories.add(CENTRAL);
        }
        return repositories;
    }

    /**
     * Resolves all dependency artifacts using a file cache resolver
     * <p>
     * Note: this will resolve both build-plugin dependencies and normal dependencies.
     *
     * @return list of resolved artifacts.
     */
    public static List<ResolvedArtifact> resolveArtifactsWithFileCache(SmithyBuildConfig smithyBuildConfig,
                                                                BuildOptions buildOptions,
                                                                MavenConfig maven,
                                                                DependencyResolver baseResolver

    ) {
        long lastModified = smithyBuildConfig.getLastModifiedInMillis();
        DependencyResolver delegate = new FilterCliVersionResolver(SmithyCli.getVersion(), baseResolver);
        DependencyResolver resolver = new FileCacheResolver(getCacheFile(buildOptions, smithyBuildConfig),
                lastModified,
                delegate);

        Set<MavenRepository> repositories = ConfigurationUtils.getConfiguredMavenRepos(smithyBuildConfig);
        repositories.forEach(resolver::addRepository);

        // Use the pinned lockfile dependencies if a lockfile exists otherwise use the configured dependencies
        Optional<LockFile> lockFileOptional = LockFile.load();
        if (lockFileOptional.isPresent()) {
            LockFile lockFile = lockFileOptional.get();
            if (lockFile.getConfigHash() != ConfigurationUtils.configHash(maven.getAllDependencies(), repositories)) {
                throw new CliError(
                        "`smithy-lock.json` does not match configured dependencies. "
                                + "Re-lock dependencies using the `lock` command or revert changes.");
            }
            LOGGER.fine(() -> "`smithy-lock.json` found. Using locked dependencies: "
                    + lockFile.getDependencyCoordinateSet());
            lockFile.getDependencyCoordinateSet().forEach(resolver::addDependency);
        } else {
            maven.getAllDependencies().forEach(resolver::addDependency);
        }

        List<ResolvedArtifact> artifacts = resolver.resolve();
        LOGGER.fine(() -> "Classpath resolved with Maven: " + artifacts);
        // Ensure resolved artifacts match pinned artifacts
        lockFileOptional.ifPresent(lockFile -> lockFile.validateArtifacts(artifacts));

        return artifacts;
    }

    private static File getCacheFile(BuildOptions buildOptions, SmithyBuildConfig config) {
        return buildOptions.resolveOutput(config).resolve("classpath.json").toFile();
    }

    /**
     * Computes the combined hash of the artifact set and repositories set.
     *
     * @param artifacts set of requested artifact coordinates
     * @param repositories set of repositories used for dependency resolution
     * @return combined hash
     */
    public static int configHash(Set<String> artifacts, Set<MavenRepository> repositories) {
        int result = 0;
        for (String artifact : artifacts) {
            result = 31 * result + getFnvHash(artifact);
        }
        for (MavenRepository repo : repositories) {
            result = 31 * result + getFnvHash(repo.getUrl());
        }
        return result;
    }

    private static int getFnvHash(CharSequence text) {
        int hashCode = FNV_OFFSET_BIAS;
        for (int i = 0; i < text.length(); i++) {
            hashCode = (hashCode ^ text.charAt(i)) * FNV_PRIME;
        }
        return hashCode;
    }
}

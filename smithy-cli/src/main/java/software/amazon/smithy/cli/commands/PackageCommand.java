/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.smithy.cli.commands;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import software.amazon.smithy.build.model.MavenConfig;
import software.amazon.smithy.build.model.MavenRepository;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.cli.Arguments;
import software.amazon.smithy.cli.CliError;
import software.amazon.smithy.cli.Command;
import software.amazon.smithy.cli.SmithyCli;
import software.amazon.smithy.cli.dependencies.DependencyResolver;
import software.amazon.smithy.cli.dependencies.FilterCliVersionResolver;
import software.amazon.smithy.cli.dependencies.ResolvedArtifact;

final class PackageCommand implements Command {
    private static final Logger LOGGER = Logger.getLogger(PackageCommand.class.getName());

    private final DependencyResolver.Factory dependencyResolverFactory;
    private final String parentCommandName;


    PackageCommand(String parentCommandName, DependencyResolver.Factory dependencyResolverFactory) {
        this.parentCommandName = parentCommandName;
        this.dependencyResolverFactory = dependencyResolverFactory;
    }

    @Override
    public String getName() {
        return "package";
    }

    // TODO: add a real summary
    @Override
    public String getSummary() {
        return "Packages output into a JAR artifact.";
    }

    @Override
    public int execute(Arguments arguments, Env env) {
        arguments.addReceiver(new ConfigOptions());

        CommandAction action = HelpActionWrapper.fromCommand(this, parentCommandName, this::run);

        return action.apply(arguments, env);
    }

    private int run(Arguments arguments, Env env) {
        ConfigOptions configOptions = arguments.getReceiver(ConfigOptions.class);
        SmithyBuildConfig smithyBuildConfig = configOptions.createSmithyBuildConfig();

        // TODO: Extract out common behavior with lock command
        DependencyResolver baseResolver = dependencyResolverFactory.create(smithyBuildConfig, env);
        DependencyResolver resolver = new FilterCliVersionResolver(SmithyCli.getVersion(), baseResolver);

        Set<MavenRepository> repositories = ConfigurationUtils.getConfiguredMavenRepos(smithyBuildConfig);
        repositories.forEach(resolver::addRepository);

        MavenConfig maven = smithyBuildConfig.getMaven().get();

        // Use the pinned lockfile dependencies if a lockfile exists otherwise use the configured dependencies
        Optional<LockFile> lockFileOptional = LockFile.load();
        Set<String> runtimeDeps = maven.getDependencies();

        if (lockFileOptional.isPresent()) {
            LockFile lockFile = lockFileOptional.get();
            if (lockFile.getConfigHash() != ConfigurationUtils.configHash(maven.getAllDependencies(), repositories)) {
                throw new CliError(
                        "`smithy-lock.json` does not match configured dependencies. "
                                + "Re-lock dependencies using the `lock` command or revert changes.");
            }
            LOGGER.fine(() -> "`smithy-lock.json` found. Using locked dependencies: "
                    + lockFile.getDependencyCoordinateSet());

            List<String> versionTrimmedDeps = runtimeDeps.stream().map(PackageCommand::trimVersion)
                    .collect(Collectors.toList());
            lockFile.getDependencyCoordinateSet()
                    .stream()
                    .filter(d -> versionTrimmedDeps.contains(trimVersion(d)))
                    .forEach(resolver::addDependency);
        } else {
            runtimeDeps.forEach(resolver::addDependency);
        }

        // Add all resolved artifacts to the POM
        for (ResolvedArtifact artifact : resolver.resolve()) {
            LOGGER.warning(() -> "RUNTIME ONLY resolved with Maven: " + artifact.getCoordinates());
        }

        return 0;
    }

    private static String trimVersion(String coordinates) {
        return coordinates.substring(0, coordinates.lastIndexOf(":"));
    }
}

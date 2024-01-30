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
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import software.amazon.smithy.build.model.MavenConfig;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.cli.ArgumentReceiver;
import software.amazon.smithy.cli.Arguments;
import software.amazon.smithy.cli.Command;
import software.amazon.smithy.cli.HelpPrinter;
import software.amazon.smithy.cli.dependencies.DependencyResolver;
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

    @Override
    public String getSummary() {
        return "Packages plugin artifacts into a JAR.";
    }

    @Override
    public int execute(Arguments arguments, Env env) {
        arguments.addReceiver(new ConfigOptions());
        arguments.addReceiver(new BuildOptions());
        arguments.addReceiver(new Options());

        CommandAction action = HelpActionWrapper.fromCommand(this, parentCommandName, this::run);

        return action.apply(arguments, env);
    }

    private static final class Options implements ArgumentReceiver {
        private String projection;

        @Override
        public Consumer<String> testParameter(String name) {
            if (name.equals("--projection")) {
                return value -> projection = value;
            }
            return null;
        }

        @Override
        public void registerHelp(HelpPrinter printer) {
            printer.param("--projection", null, "PROJECTION_NAME",
                    "Only package artifacts for this projection.");
        }
    }

    private int run(Arguments arguments, Env env) {
        ConfigOptions configOptions = arguments.getReceiver(ConfigOptions.class);
        BuildOptions buildOptions = arguments.getReceiver(BuildOptions.class);
        SmithyBuildConfig smithyBuildConfig = configOptions.createSmithyBuildConfig();
        Options options = arguments.getReceiver(Options.class);
        // Resolve dependencies to use for packaging
        resolveRuntimeDependencies(smithyBuildConfig, buildOptions, env);

        return 0;
    }

    private List<ResolvedArtifact> resolveRuntimeDependencies(SmithyBuildConfig smithyBuildConfig,
                                                              BuildOptions buildOptions,
                                                              Env env
    ) {
        DependencyResolver baseResolver = dependencyResolverFactory.create(smithyBuildConfig, env);

        // Packaged artifacts should not contain dependency ranges, so we need to
        // resolve the runtimeDependencies so no ranges are used for the artifact pom.xml
        MavenConfig maven = smithyBuildConfig.getMaven().get();
        List<ResolvedArtifact> allDeps = ConfigurationUtils.resolveArtifactsWithFileCache(
                smithyBuildConfig, buildOptions, maven, baseResolver
        );

        // Filter out build-plugin deps
        List<String> versionTrimmedDeps = maven.getDependencies().stream()
                .map(PackageCommand::trimVersion)
                .collect(Collectors.toList());
        List<ResolvedArtifact> runtimeDeps = allDeps.stream()
                .filter(d -> versionTrimmedDeps.contains(trimVersion(d.getCoordinates())))
                .collect(Collectors.toList());

        LOGGER.warning("FOUND RUNTIME DEPS: " + runtimeDeps);

        return runtimeDeps;
    }

    private static String trimVersion(String coordinates) {
        return coordinates.substring(0, coordinates.lastIndexOf(":"));
    }
}

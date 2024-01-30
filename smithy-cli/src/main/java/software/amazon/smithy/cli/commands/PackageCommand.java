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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.SmithyBuild;
import software.amazon.smithy.build.model.MavenConfig;
import software.amazon.smithy.build.model.PackagingConfig;
import software.amazon.smithy.build.model.ProjectionConfig;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.cli.ArgumentReceiver;
import software.amazon.smithy.cli.Arguments;
import software.amazon.smithy.cli.CliError;
import software.amazon.smithy.cli.Command;
import software.amazon.smithy.cli.HelpPrinter;
import software.amazon.smithy.cli.SmithyCli;
import software.amazon.smithy.cli.dependencies.DependencyResolver;
import software.amazon.smithy.cli.dependencies.ResolvedArtifact;

final class PackageCommand implements Command {
    private static final Logger LOGGER = Logger.getLogger(PackageCommand.class.getName());
    private static final String SOURCES = "sources";
    private static final String SMITHY_SOURCE_PREFIX= "META-INF/smithy/";
    private static final int BUFFER_SIZE = 1024;
    private static final String BUILD_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private final DependencyResolver.Factory dependencyResolverFactory;
    private final String parentCommandName;

    // TODO: Make this per-packager?
    private static final String PACKAGING_OUTPUT = "release/jar";

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
        List<ResolvedArtifact> runtimeDeps = resolveRuntimeDependencies(smithyBuildConfig, buildOptions, env);
        LOGGER.warning("FOUND RUNTIME DEPS: " + runtimeDeps);

        // Prepare the config so it contains the build info
        // TODO: Improve the approach to get a prepared config
        smithyBuildConfig = SmithyBuild.create(env.classLoader()).config(smithyBuildConfig).getPreparedConfig();

        LOGGER.warning("PROJECTIONS " + smithyBuildConfig.getProjections());

        // List through each of the projections
        // TODO: Maybe just go through the directories?
        Path outputDirectory = buildOptions.resolveOutput(smithyBuildConfig);
        if (options.projection != null) {
            ProjectionConfig projectionConfig = smithyBuildConfig.getProjections().get(options.projection);
            if (projectionConfig == null) {
                throw new CliError("Could not find projection `" + options
                        + "` in build config.");
            }
            projectionConfig.getPackaging().ifPresent(p -> {
                packageProjection(options.projection, p, outputDirectory, runtimeDeps);
            });
        } else {
            for (Map.Entry<String, ProjectionConfig> projectionEntry : smithyBuildConfig.getProjections().entrySet()) {
                LOGGER.warning("PACKAGING PROJECTION " + projectionEntry.getKey());
                LOGGER.warning("PACKAGING WITH PACKAGING? " + projectionEntry.getValue().getPackaging().isPresent());
                projectionEntry.getValue().getPackaging().ifPresent(p -> {
                    packageProjection(projectionEntry.getKey(), p, outputDirectory, runtimeDeps);
                });
            }
        }

        return 0;
    }

    private void packageProjection(String name,
                                   PackagingConfig config,
                                   Path outputDirectory,
                                   List<ResolvedArtifact> runtimeDeps
    ) {
        Path sourcesPath = outputDirectory.resolve(name).resolve(SOURCES);
        if (!Files.exists(sourcesPath)) {
            throw new CliError("Could not find generated artifacts for projection `" + name
                    + "`. Run `smithy build` before attempting to package build artifacts.");
        }
        // Check that config contains expected elements
        validateConfig(config, name);

        // Get a file manifest scoped to the ${outputDir}/${projection}/release/jar/
        FileManifest pluginPackagingManifest = FileManifest.create(outputDirectory.resolve(name).resolve(PACKAGING_OUTPUT));

        // Write pom file
        PomFile pom = new PomFile(config.getArtifactId().get(), config.getGroupId().get(), config.getVersion().get());
        pom.addDependencies(runtimeDeps);
        config.getPomData().ifPresent(pom::addAdditionalNodes);
        pom.write(pluginPackagingManifest);

        // TODO: add trait codegen?
        Path jarFilePath = pluginPackagingManifest.addFile(Paths.get(
                config.getArtifactId().get() + "-" + config.getVersion().get() + ".jar"));
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFilePath), getJarManifest(config))) {
            for (Path srcsPath : listContents(sourcesPath.toFile())) {
                LOGGER.warning("FOUND SOURCES PATH " + srcsPath);
                String jarPath = getJarPath(sourcesPath, SMITHY_SOURCE_PREFIX, srcsPath);
                LOGGER.warning("TRANSLATED TO JAR PATH " + jarPath);
                writeJarEntry(jarPath, srcsPath, jos);
            }
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }

        LOGGER.warning("PACKAGING PROJECTION: " + name);
    }

    private static void writeJarEntry(String fileName, Path path, JarOutputStream target) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            writeJarEntry(fileName, is, target);
        }
    }

    private String getJarPath(Path root, String prefix, Path src) {
        return prefix + root.relativize(src);
    }

    public List<Path> listContents(File directory) {
        LOGGER.warning("LISTING FOR " + directory);
        List<Path> contents = new ArrayList<>();
        for (final File fileEntry : Objects.requireNonNull(directory.listFiles())) {
            if (fileEntry.isDirectory()) {
                contents.addAll(listContents(fileEntry));
            } else {
                contents.add(fileEntry.toPath());
            }
        }
        return contents;
    }

    private static void writeJarEntry(String fileName, InputStream inputStream, JarOutputStream target)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        target.putNextEntry(new JarEntry(fileName));
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            target.write(buffer, 0, bytesRead);
        }
        target.closeEntry();
    }


    private static void validateConfig(PackagingConfig config, String projectionName) {
        config.getVersion().orElseThrow(
                () -> new CliError("`version` not configured for projection `" + projectionName + "`."));
        config.getGroupId().orElseThrow(
                () -> new CliError("`groupId` not configured for projection `" + projectionName + "`."));
        config.getArtifactId().orElseThrow(
                () -> new CliError("`artifactId` not configured for projection `" + projectionName + "`."));
    }


    private static Manifest getJarManifest(PackagingConfig config) {
        final Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(new Attributes.Name("Build-Timestamp"),
                new SimpleDateFormat(BUILD_TIMESTAMP_FORMAT).format(new Date()));
        attributes.put(new Attributes.Name("Created-With"), "Smithy-Jar-Plugin (" + SmithyCli.getVersion() + ")");

        if (!config.getTags().isEmpty()) {
            attributes.put(new Attributes.Name("Smithy-Tags"), String.join(",", config.getTags()));
        }

        for (Map.Entry<String, String> headerEntry : config.getManifestHeaders().entrySet()) {
            attributes.put(new Attributes.Name(headerEntry.getKey()), headerEntry.getValue());
        }

        return manifest;
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

        return runtimeDeps;
    }

    private static String trimVersion(String coordinates) {
        return coordinates.substring(0, coordinates.lastIndexOf(":"));
    }
}

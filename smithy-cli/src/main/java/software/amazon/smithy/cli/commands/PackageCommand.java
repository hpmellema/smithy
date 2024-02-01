/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.cli.commands;

import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.model.MavenConfig;
import software.amazon.smithy.build.model.PackagingConfig;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.cli.Arguments;
import software.amazon.smithy.cli.CliError;
import software.amazon.smithy.cli.CliPrinter;
import software.amazon.smithy.cli.ColorBuffer;
import software.amazon.smithy.cli.ColorFormatter;
import software.amazon.smithy.cli.ColorTheme;
import software.amazon.smithy.cli.Command;
import software.amazon.smithy.cli.StandardOptions;
import software.amazon.smithy.cli.dependencies.DependencyResolver;
import software.amazon.smithy.cli.dependencies.ResolvedArtifact;
import software.amazon.smithy.utils.ListUtils;

final class PackageCommand implements Command {
    private static final Logger LOGGER = Logger.getLogger(PackageCommand.class.getName());
    private static final String SOURCES = "sources";
    private static final String SMITHY_SOURCE_PREFIX = "META-INF/smithy/";
    private static final String TRAIT_CODEGEN = "trait-codegen";
    private static final String PACKAGING_OUTPUT = "release/jar";

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
        arguments.addReceiver(new PackagingOptions());

        CommandAction action = HelpActionWrapper.fromCommand(this, parentCommandName, this::run);

        return action.apply(arguments, env);
    }

    private int run(Arguments arguments, Env env) {
        BuildOptions buildOptions = arguments.getReceiver(BuildOptions.class);
        ConfigOptions configOptions = arguments.getReceiver(ConfigOptions.class);
        StandardOptions standardOptions = arguments.getReceiver(StandardOptions.class);
        SmithyBuildConfig smithyBuildConfig = configOptions.createSmithyBuildConfig();
        PackagingOptions options = arguments.getReceiver(PackagingOptions.class);
        ResultConsumer consumer = new ResultConsumer(env.colors(), env.stderr(), standardOptions.quiet());

        if (!standardOptions.quiet()) {
            env.colors().println(env.stderr(), "Packaging projections...", ColorTheme.MUTED);
            env.stderr().println("");
        }

        packageAllProjections(options.getPackagingConfigs(smithyBuildConfig),
                smithyBuildConfig,
                buildOptions,
                env, consumer, consumer);

        // Throw an exception if any errors occurred
        if (!consumer.failedProjections.isEmpty()) {
            consumer.failedProjections.sort(String::compareTo);
            StringBuilder error = new StringBuilder();
            try (ColorBuffer buffer = ColorBuffer.of(env.colors(), error)) {
                buffer.println();
                buffer.println(String.format(
                        "Packaging failed for the following %d Smith projection(s): %s",
                        consumer.failedProjections.size(),
                        consumer.failedProjections));
            }
            throw new CliError(error.toString());
        }

        // Print out the summary
        if (!standardOptions.quiet()) {
            try (ColorBuffer buffer = ColorBuffer.of(env.colors(), env.stderr())) {
                buffer.style(w -> w.append("Summary: "), ColorTheme.TEMPLATE_TITLE);
                buffer.print(String.format("packaged %s artifacts for %s projections",
                        consumer.artifactCount.get(),
                        consumer.projectionCount.get()
                ));
                buffer.println();
            }
        }

        return 0;
    }

    private static final class ResultConsumer implements Consumer<PackagingResult>, BiConsumer<String, Throwable> {
        private final List<String> failedProjections = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger artifactCount = new AtomicInteger();
        private final AtomicInteger projectionCount = new AtomicInteger();
        private final boolean quiet;
        private final ColorFormatter colors;
        private final CliPrinter printer;

        ResultConsumer(ColorFormatter colors, CliPrinter stderr, boolean quiet) {
            this.colors = colors;
            this.printer = stderr;
            this.quiet = quiet;
        }

        @Override
        public void accept(String name, Throwable exception) {
            failedProjections.add(name);
            StringWriter writer = new StringWriter();
            writer.write(String.format("%nProjection %s failed packaging: %s%n", name, exception.toString()));
            exception.printStackTrace(new PrintWriter(writer));
            colors.println(printer, writer.toString(), ColorTheme.ERROR);
        }

        @Override
        public void accept(PackagingResult result) {
            try (ColorBuffer buffer = ColorBuffer.of(colors, printer)) {
                // Increment projection count
                projectionCount.incrementAndGet();

                // Increment the total number of artifacts written.
                artifactCount.addAndGet(result.manifest.getFiles().size());

                if (!quiet) {
                    int remainingLength = 80 - 6 - result.name.length();
                    buffer.style(w -> {
                        w.append("──  ");
                        w.append(result.name);
                        w.append("  ");
                        for (int i = 0; i < remainingLength; i++) {
                            w.append("─");
                        }
                        w.println();
                    }, ColorTheme.NOTE);
                    buffer.print("successfully packaged projection ")
                            .append(result.name);
                    if (result.includedTraits) {
                        buffer.append(" (traits generated)");
                    }
                    buffer.println();
                }

                buffer.println();
            }
        }
    }

    private void packageAllProjections(Map<String, PackagingConfig> packagingConfigs,
                                       SmithyBuildConfig smithyBuildConfig,
                                       BuildOptions buildOptions,
                                       Env env,
                                       Consumer<PackagingResult> resultCallback,
                                       BiConsumer<String, Throwable> exceptionCallback
    ) {
        // Resolve dependencies to use for packaging
        DependencyResolver baseResolver = dependencyResolverFactory.create(smithyBuildConfig, env);

        // Packaged artifacts should not contain dependency ranges, so we need to
        // resolve the runtimeDependencies so no ranges are used for the artifact pom.xml
        MavenConfig maven = smithyBuildConfig.getMaven().orElse(null);

        // Set up a resolver that does not filter out the Smithy deps
        List<ResolvedArtifact> allDeps = ConfigurationUtils.resolveArtifacts(smithyBuildConfig, maven, baseResolver);
        List<ResolvedArtifact> runtimeDeps = filterOutBuildDeps(maven, allDeps);
        LOGGER.fine(() -> String.format("Including the following artifacts as runtime dependencies: %s", runtimeDeps));

        List<Path> allDepPaths = allDeps.stream().map(ResolvedArtifact::getPath)
                .collect(Collectors.toList());
        String buildClasspath = getClasspathFromEnv(allDepPaths, env);
        LOGGER.fine(() -> String.format("Using the following classpath for packaging [%s]", buildClasspath));

        Path outputDirectory = buildOptions.resolveOutput(smithyBuildConfig);

        for (Map.Entry<String, PackagingConfig> entry : packagingConfigs.entrySet()) {
            PackagingResult result = null;
            try {
                result = packageProjection(entry.getKey(), entry.getValue(),
                        outputDirectory, runtimeDeps, buildClasspath);
            } catch (Throwable exc){
                exceptionCallback.accept(entry.getKey(), exc);
            }
            resultCallback.accept(result);
        }
    }

    private String getClasspathFromEnv(List<Path> artifacts, Env env) {
        try (URLClassLoader loader = createClassLoaderFromPaths(artifacts, env.classLoader())) {
            return Arrays.stream(loader.getURLs())
                    .map(URL::getPath)
                    .collect(Collectors.joining(":")) + ":";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static URLClassLoader createClassLoaderFromPaths(Collection<Path> artifacts, ClassLoader parent) {
        return new URLClassLoader(createUrlsFromPaths(artifacts), parent);
    }

    private static URL[] createUrlsFromPaths(Collection<Path> paths) {
        URL[] urls = new URL[paths.size()];
        int i = 0;
        for (Path artifact : paths) {
            try {
                urls[i++] = artifact.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new CliError("Error creating class loader: " + artifact);
            }
        }
        return urls;
    }

    private static final class PackagingResult {
        private final String name;
        private final FileManifest manifest;
        private final boolean includedTraits;

        private PackagingResult(String name, FileManifest manifest, boolean includedTraits) {
            this.name = name;
            this.manifest = manifest;
            this.includedTraits = includedTraits;
        }
    }

    private PackagingResult packageProjection(String name,
                                              PackagingConfig config,
                                              Path outputDirectory,
                                              List<ResolvedArtifact> runtimeDeps,
                                              String buildClasspath
    ) {
        Path sourcesPath = outputDirectory.resolve(name).resolve(SOURCES);
        if (!Files.exists(sourcesPath)) {
            throw new CliError("Could not find generated artifacts for projection `" + name
                    + "`. Run `smithy build` before attempting to package build artifacts.");
        }

        // Check that config contains expected elements
        validateConfig(config, name);

        // Get a file manifest scoped to the ${outputDir}/${projection}/release/jar/
        FileManifest pluginPackagingManifest = FileManifest.create(outputDirectory.resolve(name)
                .resolve(PACKAGING_OUTPUT));

        // Write pom file
        // Poms are usually named ${artifactId}-${version}.pom by both Gradle and
        // maven. We follow the same convention here.
        Path pomPath = pluginPackagingManifest.addFile(Paths.get(config.getArtifactId().get() + "-"
                + config.getVersion().get() + ".pom"));
        PomFile pom = new PomFile(config.getArtifactId().get(), config.getGroupId().get(), config.getVersion().get());
        pom.addDependencies(runtimeDeps);
        config.getPomData().ifPresent(pom::addAdditionalNodes);
        pom.write(pomPath);

        // Create the output jar artifacts
        Path traitCodegenPluginPath = outputDirectory.resolve(name).resolve(TRAIT_CODEGEN);
        Path jarFilePath = pluginPackagingManifest.addFile(Paths.get(
                config.getArtifactId().get() + "-" + config.getVersion().get() + ".jar"));
        JarFileBuilder baseJar = new JarFileBuilder(config);
        baseJar.addSourceFiles(sourcesPath, SMITHY_SOURCE_PREFIX);
        boolean includeTraits = Files.exists(traitCodegenPluginPath);
        if (includeTraits) {
            LOGGER.fine(() -> String.format("Writing sources jar for artifact: %s", config.getArtifactId()));
            Path srcsJarPath = pluginPackagingManifest.addFile(Paths.get(
                    config.getArtifactId().get() + "-" + config.getVersion().get() + "-sources" + ".jar"));
            JarFileBuilder sourcesJar = new JarFileBuilder(config);
            sourcesJar.addSourceFiles(sourcesPath, SMITHY_SOURCE_PREFIX);
            sourcesJar.addSourceFiles(traitCodegenPluginPath, "", p -> !p.toString().contains("compiled/"));
            sourcesJar.write(srcsJarPath);

            // Generate compiled sources and add them to the base jar along with any generated metadata files
            LOGGER.fine(() -> String.format("Compiling generated trait sources for artifact %s with classpath: %s",
                    config.getArtifactId(), buildClasspath));
            baseJar.addClassFiles(compileSources(buildClasspath, traitCodegenPluginPath), "compiled/");
            baseJar.addSourceFiles(traitCodegenPluginPath.resolve("META-INF"), "META-INF/");
        }
        baseJar.write(jarFilePath);

        return new PackagingResult(name, pluginPackagingManifest, includeTraits);
    }

    private Iterable<? extends JavaFileObject> compileSources(String buildClasspath, Path rootPath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CliError("Could not load jdk.compiler");
        }

        DiagnosticCollector<? super JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = new ArrayList<>(ListUtils.of("--class-path", buildClasspath,
                "-d", rootPath.toString() + "/compiled"));
        StringWriter out = new StringWriter();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            LOGGER.fine("Starting compilation");
            // Do not include any META-INF files in compilation targets
            List<File> contents = ConfigurationUtils.listContents(rootPath.toFile())
                    .stream()
                    .filter(p -> !p.toString().contains("META-INF"))
                    .filter(p -> !p.toString().contains("compiled"))
                    .map(Path::toFile).collect(Collectors.toList());
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(contents);
            JavacTask task = (JavacTask) compiler.getTask(out, fileManager, diagnostics, options, null, compilationUnits);
            Iterable<? extends JavaFileObject>  generated = task.generate();
            LOGGER.fine(() -> String.format("Compilation Generated: [%s]", generated));
            return generated;
        } catch (IOException exception) {
            throw new CliError("Failed to compile Trait codegen classes. Compilation output: " + out);
        }
    }

    private static void validateConfig(PackagingConfig config, String projectionName) {
        config.getVersion().orElseThrow(
                () -> new CliError("`version` not configured for projection `" + projectionName + "`."));
        config.getGroupId().orElseThrow(
                () -> new CliError("`groupId` not configured for projection `" + projectionName + "`."));
        config.getArtifactId().orElseThrow(
                () -> new CliError("`artifactId` not configured for projection `" + projectionName + "`."));
    }

    private static List<ResolvedArtifact> filterOutBuildDeps(MavenConfig maven, List<ResolvedArtifact> allDeps) {
        // Filter out build-plugin deps
        if (maven == null) {
            return allDeps;
        }
        List<String> versionTrimmedDeps = maven.getDependencies().stream()
                .map(PackageCommand::trimVersion)
                .collect(Collectors.toList());
        return allDeps.stream()
                .filter(d -> versionTrimmedDeps.contains(trimVersion(d.getCoordinates())))
                .collect(Collectors.toList());
    }

    private static String trimVersion(String coordinates) {
        return coordinates.substring(0, coordinates.lastIndexOf(":"));
    }
}

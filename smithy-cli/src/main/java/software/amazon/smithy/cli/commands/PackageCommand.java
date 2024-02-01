/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.cli.commands;

import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
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
import software.amazon.smithy.utils.IoUtils;
import software.amazon.smithy.utils.ListUtils;

final class PackageCommand implements Command {
    private static final Logger LOGGER = Logger.getLogger(PackageCommand.class.getName());
    private static final String SOURCES = "sources";
    private static final String SMITHY_SOURCE_PREFIX = "META-INF/smithy/";
    private static final String TRAIT_CODEGEN = "trait-codegen";
    private static final int BUFFER_SIZE = 1024;
    private static final String BUILD_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    // TODO: Make this per-packager?
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
        arguments.addReceiver(new Options());

        CommandAction action = HelpActionWrapper.fromCommand(this, parentCommandName, this::run);

        return action.apply(arguments, env);
    }

    private int run(Arguments arguments, Env env) {
        BuildOptions buildOptions = arguments.getReceiver(BuildOptions.class);
        ConfigOptions configOptions = arguments.getReceiver(ConfigOptions.class);
        SmithyBuildConfig smithyBuildConfig = configOptions.createSmithyBuildConfig();
        Options options = arguments.getReceiver(Options.class);

        // Resolve dependencies to use for packaging
        DependencyResolver baseResolver = dependencyResolverFactory.create(smithyBuildConfig, env);

        // Packaged artifacts should not contain dependency ranges, so we need to
        // resolve the runtimeDependencies so no ranges are used for the artifact pom.xml
        MavenConfig maven = smithyBuildConfig.getMaven().get();

        // Set up a resolver that does not filter out the Smithy deps
        List<ResolvedArtifact> allDeps = ConfigurationUtils.resolveArtifacts(smithyBuildConfig, maven, baseResolver);
        List<ResolvedArtifact> runtimeDeps = filterOutBuildDeps(maven, allDeps);
        LOGGER.warning("FOUND RUNTIME DEPS: " + runtimeDeps);

        List<Path> allDepPaths = allDeps.stream().map(ResolvedArtifact::getPath)
                .collect(Collectors.toList());
        String buildClasspath = getClasspathFromEnv(allDepPaths, env);
        LOGGER.warning("FOUND CLASSPATH : " + buildClasspath);

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
                packageProjection(options.projection, p, outputDirectory, runtimeDeps, buildClasspath);
            });
        } else {
            for (Map.Entry<String, ProjectionConfig> projectionEntry : smithyBuildConfig.getProjections().entrySet()) {
                LOGGER.warning("PACKAGING PROJECTION " + projectionEntry.getKey());
                LOGGER.warning("PACKAGING WITH PACKAGING? " + projectionEntry.getValue().getPackaging().isPresent());
                projectionEntry.getValue().getPackaging().ifPresent(p -> {
                    packageProjection(projectionEntry.getKey(), p, outputDirectory, runtimeDeps, buildClasspath);
                });
            }
        }
        return 0;
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

    // TODO: How to get smithy-model, smithy-util, etc into the classpath?
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


    private void packageProjection(String name,
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
        PomFile pom = new PomFile(config.getArtifactId().get(), config.getGroupId().get(), config.getVersion().get());
        pom.addDependencies(runtimeDeps);
        config.getPomData().ifPresent(pom::addAdditionalNodes);
        pom.write(pluginPackagingManifest);

        // TODO: also create a sources jar if trait codegen exists
        // Create the output jar artifact
        Path traitCodegenPluginPath = outputDirectory.resolve(name).resolve(TRAIT_CODEGEN);
        Path jarFilePath = pluginPackagingManifest.addFile(Paths.get(
                config.getArtifactId().get() + "-" + config.getVersion().get() + ".jar"));
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFilePath), getJarManifest(config))) {
            addFilesToJar(sourcesPath, SMITHY_SOURCE_PREFIX, jos);

            // if trait codegen is found, execute trait codegen compilation and copy all trait codegen
            // service and class files into output jar
            if (Files.exists(traitCodegenPluginPath)) {
                Iterable<? extends JavaFileObject> generated = compileSources(buildClasspath, traitCodegenPluginPath);
                addFilesToJar(traitCodegenPluginPath.resolve("META-INF"), "META-INF/", jos);
                addJavaClassesToJar(generated, jos);
            }
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }

        LOGGER.warning("PACKAGING PROJECTION: " + name);
    }

    private static void addJavaClassesToJar(Iterable<? extends JavaFileObject> generated, JarOutputStream jos)
            throws IOException {
        for (JavaFileObject classfile : generated) {
            try (InputStream is = classfile.openInputStream()) {
                writeJarEntry(classfile.getName().replaceFirst("^compiled/",""), is, jos);
            }
        }
    }

    private Iterable<? extends JavaFileObject> compileSources(String buildClasspath, Path rootPath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CliError("Could not load jdk.compiler");
        }

        DiagnosticCollector<? super JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = new ArrayList<>(ListUtils.of("--class-path", buildClasspath, "-d", "compiled"));
        StringWriter out = new StringWriter();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            LOGGER.warning("Starting compilation");
            // Do not include any META-INF files in compilation targets
            List<File> contents = listContents(rootPath.toFile())
                    .stream()
                    .filter(p -> !p.toString().contains("META-INF"))
                    .map(Path::toFile).collect(Collectors.toList());
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(contents);
            JavacTask task = (JavacTask) compiler.getTask(out, fileManager, diagnostics, options, null, compilationUnits);
            Iterable<? extends JavaFileObject>  generated = task.generate();
            LOGGER.warning("COMPILATION FINISHED. Generated: " + generated);
            return generated;
        } catch (IOException exception) {
            throw new CliError("Failed to compile Trait codegen classes. Compilation output: " + out);
        }
    }

    private static void addFilesToJar(Path sourcePath, String prefix, JarOutputStream jos) throws IOException {
        for (Path src : listContents(sourcePath.toFile())) {
            String jarPath = prefix + sourcePath.relativize(src);
            writeJarEntry(jarPath, src, jos);
        }
    }

    private static void writeJarEntry(String fileName, Path path, JarOutputStream target) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            writeJarEntry(fileName, is, target);
        }
    }

    private static List<Path> listContents(File directory) {
        List<Path> contents = new ArrayList<>();
        for (File fileEntry : Objects.requireNonNull(directory.listFiles())) {
            if (fileEntry.isDirectory()) {
                    contents.addAll(listContents(fileEntry));
            } else {
                contents.add(Objects.requireNonNull(fileEntry).toPath());
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


    private static List<ResolvedArtifact> filterOutBuildDeps(MavenConfig maven, List<ResolvedArtifact> allDeps) {
        // Filter out build-plugin deps
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

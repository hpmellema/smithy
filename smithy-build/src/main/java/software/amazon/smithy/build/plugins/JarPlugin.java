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

package software.amazon.smithy.build.plugins;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.BufferOverflowException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import java.util.stream.Stream;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelDiscovery;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NodeMapper;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ModelSerializer;
import software.amazon.smithy.utils.IoUtils;
import software.amazon.smithy.utils.ListUtils;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 *
 */
public final class JarPlugin implements SmithyBuildPlugin {
    private static final String JAR_FILE_PREFIX = "META-INF/smithy/";
    private static final int BUFFER_SIZE = 1024;
    private static final String BUILD_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final String NAME = "library-jar";
    private static final String PROJECTED_FILENAME = "model.json";
    private static final Logger LOGGER = Logger.getLogger(JarPlugin.class.getName());

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void execute(PluginContext context) {
        if (!context.getOriginalModel().isPresent()) {
            LOGGER.warning("No original model was provided, so the sources plugin cannot run");
            return;
        }

        NodeMapper mapper = new NodeMapper();
        mapper.setWhenMissingSetter(NodeMapper.WhenMissing.IGNORE);
        Settings settings = mapper.deserializeInto(context.getSettings(), new Settings());

        List<String> names;
        String projectionName = context.getProjectionName();

        Path jarFilePath = context.getFileManifest().addFile(Paths.get(projectionName + ".jar"));
        try (FileOutputStream fileOutputStream = new FileOutputStream(new File(jarFilePath.toUri()));
             JarOutputStream jarOutputStream = new JarOutputStream(fileOutputStream, getJarManifest(settings));
        ) {
            if (projectionName.equals("source")) {
                // Copy sources directly.
                names = copySources(context, jarOutputStream);
                LOGGER.fine(() -> String.format("Copying source files to the sources of %s: %s",
                        projectionName, names));
            } else {
                // Extract source shapes, traits, and metadata from the projected model.
                LOGGER.fine(() -> String.format(
                        "Creating the `%s` sources by extracting relevant components from the original model",
                        projectionName));
                names = ListUtils.of(PROJECTED_FILENAME);
                projectSources(context, jarOutputStream);
            }

            String manifest = SourceUtils.writeSmithyManifest(names, projectionName);
            writeJarEntry("manifest", manifest, jarOutputStream);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
        context.getFileManifest().addFile(jarFilePath);
    }

    @SmithyInternalApi
    public static final class Settings {
        private List<String> tags = Collections.emptyList();
        private Map<String, String> manifestHeaders = Collections.emptyMap();

        public void tags(List<String> tags) {
            this.tags = tags;
        }

        public void manifestHeaders(Map<String, String> manifestHeaders) {
            this.manifestHeaders = manifestHeaders;
        }
    }

    private static void writeJarEntry(String fileName, String contents, JarOutputStream target) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
            writeJarEntry(fileName, inputStream, target);
        }
    }

    private static void writeJarEntry(String fileName, InputStream inputStream, JarOutputStream target) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        target.putNextEntry(new JarEntry(JAR_FILE_PREFIX + fileName));
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            target.write(buffer, 0, bytesRead);
        }
        target.closeEntry();
    }

    private static Manifest getJarManifest(Settings settings) {
        final Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(new Attributes.Name("Build-Timestamp"), new SimpleDateFormat(BUILD_TIMESTAMP_FORMAT).format(new Date()));
        attributes.put(new Attributes.Name("Created-With"), "Smithy-Jar-Plugin (" + getVersion() + ")");

        if (!settings.tags.isEmpty()) {
            attributes.put(new Attributes.Name("Smithy-Tags"), String.join(",", settings.tags));
        }

        for (Map.Entry<String, String> headerEntry : settings.manifestHeaders.entrySet()) {
            attributes.put(new Attributes.Name(headerEntry.getKey()), headerEntry.getValue());
        }

        return manifest;
    }

    private static String getVersion() {
        return IoUtils.readUtf8Resource(JarPlugin.class, "jar-plugin-version").trim();
    }

    private static List<String> copySources(PluginContext context, JarOutputStream jarOutputStream) {
        List<String> names = new ArrayList<>();
        context.getSources().forEach(path -> copyDirectory(names, context.getFileManifest(), path, path,
                jarOutputStream));
        return names;
    }

    private static void copyDirectory(List<String> names, FileManifest manifest, Path root, Path current,
                                      JarOutputStream output) {
        try {
            if (Files.isDirectory(current)) {
                try (Stream<Path> fileList = Files.list(current)) {
                    fileList.filter(p -> !p.equals(current))
                            .filter(p -> Files.isDirectory(p) || Files.isRegularFile(p))
                            .forEach(p -> copyDirectory(names, manifest, p, p, output));
                }
            } else if (Files.isRegularFile(current)) {
                if (current.toString().endsWith(".jar")) {
                    // Account for just a simple file vs recursing into directories.
                    String jarRoot = root.equals(current) ? "" : (root.relativize(current) + File.separator);
                    // Copy Smithy models out of the JAR.
                    copyModelsFromJar(names, jarRoot, current, output);
                } else {
                    // Account for just a simple file vs recursing into directories.
                    Path target = root.equals(current) ? current.getFileName() : root.relativize(current);
                    copyFileToJar(names, target, IoUtils.readUtf8File(current), output);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading the contents of " + current + ": " + e.getMessage(), e);
        }
    }

    private static void copyFileToJar(List<String> names,
                                      Path target,
                                      String contents,
                                      JarOutputStream jarOutputStream
    ) throws IOException {
        // Path#getFileName might return null.
        if (target == null) {
            return;
        }

        String filename = target.toString();
        if (filename.endsWith(".smithy") || filename.endsWith(".json")) {
            writeJarEntry(filename, contents, jarOutputStream);
            names.add(target.toString());
        } else {
            LOGGER.warning("Omitting unrecognized file from Smithy model manifest: " + filename);
        }
    }

    private static void projectSources(PluginContext context, JarOutputStream outputStream) throws IOException {
        Model updatedModel = context.getModel();

        // New shapes, trait definitions, and metadata are considered "sources".
        ObjectNode serialized = ModelSerializer
                .builder()
                .shapeFilter(context::isSourceShape)
                .metadataFilter(context::isSourceMetadata)
                .build()
                .serialize(updatedModel);

        writeJarEntry(PROJECTED_FILENAME, Node.prettyPrintJson(serialized).trim() + "\n", outputStream);
    }

    private static void copyModelsFromJar(List<String> names,
                                          String jarRoot,
                                          Path jarPath,
                                          JarOutputStream output
    ) throws IOException {
        LOGGER.fine(() -> "Copying models from JAR " + jarPath);
        URL manifestUrl = ModelDiscovery.createSmithyJarManifestUrl(jarPath.toString());

        String prefix = SourceUtils.computeJarFilePrefix(jarRoot, jarPath);
        for (URL model : ModelDiscovery.findModels(manifestUrl)) {
            String name = ModelDiscovery.getSmithyModelPathFromJarUrl(model);
            Path target = Paths.get(prefix + name);
            LOGGER.finer(() -> "Copying " + name + " from JAR to " + target);
            try (InputStream is = model.openStream()) {
                copyFileToJar(names, target, IoUtils.toUtf8String(is), output);
            }
        }
    }
}

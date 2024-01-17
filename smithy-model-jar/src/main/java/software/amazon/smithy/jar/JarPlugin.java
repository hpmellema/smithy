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

package software.amazon.smithy.jar;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.build.plugins.SourcesPlugin;
import software.amazon.smithy.model.node.NodeMapper;
import software.amazon.smithy.utils.IoUtils;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Copies model sources into a JAR.
 *
 * // TODO: ADD FULL DOCS
 * <p>This plugin can only run if an original model is provided.
 */
public final class JarPlugin implements SmithyBuildPlugin {
    private static final int BUFFER_SIZE = 1024;
    private static final String BUILD_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final String NAME = "library-jar";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void execute(PluginContext context) {
        NodeMapper mapper = new NodeMapper();
        mapper.setWhenMissingSetter(NodeMapper.WhenMissing.IGNORE);
        Settings settings = mapper.deserializeInto(context.getSettings(), new Settings());

        FileManifest sourceManifest = scopeFileManifest(context.getFileManifest(), "staging/META-INF/smithy/");
        new SourcesPlugin().execute(context.toBuilder().fileManifest(sourceManifest).build());
        context.getFileManifest().addAllFiles(sourceManifest);

        // Write all traits
        // TODO: Add trait code generation

        // Write pom files
        FileManifest pomManifest = scopeFileManifest(context.getFileManifest(), "staging/META-INF/maven");
        writePoms(context.toBuilder().fileManifest(pomManifest).build(), settings);
        context.getFileManifest().addAllFiles(pomManifest);

        String projectionName = context.getProjectionName();
        Path jarFilePath = context.getFileManifest().addFile(Paths.get(projectionName + ".jar"));
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFilePath),
                getJarManifest(settings))
        ) {
            for (Path stagingPath : context.getFileManifest().getFilesIn("staging")) {
                Path jarPath = context.getFileManifest().getBaseDir().resolve("staging").relativize(stagingPath);
                writeJarEntry(jarPath.toString(), stagingPath, jarOutputStream);
            }
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    @SmithyInternalApi
    public static final class Settings {
        private String artifactId;
        private String groupdId;
        private String version;
        private List<String> tags = Collections.emptyList();
        private Map<String, String> manifestHeaders = Collections.emptyMap();

        public void tags(List<String> tags) {
            this.tags = tags;
        }

        public void manifestHeaders(Map<String, String> manifestHeaders) {
            this.manifestHeaders = manifestHeaders;
        }
    }

    private static FileManifest scopeFileManifest(FileManifest fileManifest, String scope) {
        return FileManifest.create(fileManifest.getBaseDir().resolve(scope));
    }

    private static void writePoms(PluginContext context, Settings settings) {
        // Generate the pom.properties file
        context.getFileManifest().writeFile("pom.properties", getPomPropertiesContents(settings));

        // Generate the pom.xml file
        context.getFileManifest().writeFile("pom.xml", getPomXmlContents(settings));
    }

    private static String getPomPropertiesContents(Settings settings) {
        return  "#Created by Smithy Jar Plugin " + getVersion() + "\n"
                + "groupId=" + settings.groupdId + "\n"
                + "artifactId=" + settings.artifactId + "\n"
                + "version=" + settings.version + "\n";
    }

    private static String getPomXmlContents(Settings settings) {
        return "testing testing testing";
    }

    private static void writeJarEntry(String fileName, Path path, JarOutputStream target) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            writeJarEntry(fileName, is, target);
        }
    }

    private static void writeJarEntry(String fileName, InputStream inputStream, JarOutputStream target) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        target.putNextEntry(new JarEntry(fileName));
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
}

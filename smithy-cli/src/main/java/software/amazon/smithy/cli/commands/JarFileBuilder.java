package software.amazon.smithy.cli.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.JavaFileObject;
import software.amazon.smithy.build.model.PackagingConfig;
import software.amazon.smithy.cli.SmithyCli;

final class JarFileBuilder {
    private static final int BUFFER_SIZE = 1024;
    private static final String BUILD_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private final Manifest manifest;
    private final Map<String, JavaFileObject> classFiles = new HashMap<>();
    private final Map<String, Path> sourceFiles = new HashMap();

    JarFileBuilder(PackagingConfig config) {
        manifest = getManifest(config);
    }

    public void addClassFiles(Iterable<? extends JavaFileObject> classes, String compileDirectory) {
        classes.forEach(cf -> classFiles.put(cf.getName().split(compileDirectory)[1], cf));
    }

    public void addSourceFiles(Path sourcePath, String prefix) {
        addSourceFiles(sourcePath, prefix, p -> true);
    }

    public void addSourceFiles(Path sourcePath, String prefix, Predicate<Path> filter) {
        for (Path src : ConfigurationUtils.listContents(sourcePath.toFile())) {
            if (filter.test(src)) {
                String jarPath = prefix + sourcePath.relativize(src);
                sourceFiles.put(jarPath, src);
            }
        }
    }

    public void write(Path jarPath) {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            addSourceFilesToJar(jos);
            addClassFilesToJar(jos);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    private void addSourceFilesToJar(JarOutputStream jos) throws IOException {
        for (Map.Entry<String, Path> entry : sourceFiles.entrySet()) {
            writeJarEntry(entry.getKey(), entry.getValue(), jos);
        }
    }

    private void addClassFilesToJar(JarOutputStream jos) throws IOException {
        for (Map.Entry<String, JavaFileObject> entry : classFiles.entrySet()) {
            writeJarEntry(entry.getKey(), entry.getValue().openInputStream(), jos);
        }
    }

    private static void writeJarEntry(String fileName, Path path, JarOutputStream target) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            writeJarEntry(fileName, is, target);
        }
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

    private static Manifest getManifest(PackagingConfig config) {
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
}

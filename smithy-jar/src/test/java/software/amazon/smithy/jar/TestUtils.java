package software.amazon.smithy.jar;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import software.amazon.smithy.utils.IoUtils;

public class TestUtils {
    private TestUtils() {}

    public static void assertJarContains(String buildDir, String jarPath, String... jarEntries) {
        try (JarFile jar = new JarFile(new File(buildDir, jarPath))) {
            for (String jarEntry : jarEntries) {
                Assertions.assertNotNull(jar.getEntry(jarEntry), "JAR entry `" + jarEntry + "` does not exist in `"
                        + jarPath + "`. This JAR contains the following entries:\n"
                        + Collections.list(jar.entries()).stream()
                        .map(JarEntry::getName)
                        .collect(Collectors.joining("\n")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String getVersion() {
        return IoUtils.readUtf8Resource(JarPlugin.class, "jar-plugin-version").trim();
    }
}

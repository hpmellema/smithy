package software.amazon.smithy.build.plugins;


import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class SourceUtils {
    private static final Logger LOGGER = Logger.getLogger(SourceUtils.class.getName());

    private SourceUtils() {}

    public static String writeSmithyManifest(Collection<String> sourceFileNames, String projectionName) {
        if (sourceFileNames.isEmpty()) {
            LOGGER.info(String.format("Writing empty `%s` manifest because no Smithy sources found",
                    projectionName));
            return "";
        } else {
            LOGGER.fine(() -> String.format("Writing `%s` manifest", projectionName));
            // Normalize filenames to Unix style.
            return sourceFileNames.stream().map(name -> name.replace("\\", "/"))
                    .collect(Collectors.joining("\n"));
        }
    }

    public static String computeJarFilePrefix(String jarRoot, Path jarPath) {
        Path jarFilenamePath = jarPath.getFileName();

        if (jarFilenamePath == null) {
            return jarRoot;
        }

        String jarFilename = jarFilenamePath.toString();
        return jarRoot + jarFilename.substring(0, jarFilename.length() - ".jar".length()) + File.separator;
    }

}

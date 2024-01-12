package software.amazon.smithy.build.plugins;


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

}

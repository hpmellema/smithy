package software.amazon.smithy.cli.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import software.amazon.smithy.build.model.PackagingConfig;
import software.amazon.smithy.build.model.ProjectionConfig;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.cli.ArgumentReceiver;
import software.amazon.smithy.cli.CliError;
import software.amazon.smithy.cli.HelpPrinter;
import software.amazon.smithy.utils.MapUtils;

final class PackagingOptions implements ArgumentReceiver {
    private static final Logger LOGGER = Logger.getLogger(PackagingOptions.class.getName());

    private String projection;

    String projection() {
        return (projection == null) ? "source" : projection;
    }

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

    public Map<String, PackagingConfig> getPackagingConfigs(SmithyBuildConfig buildConfig) {
        if (projection != null) {
            ProjectionConfig projectionConfig = buildConfig.getProjections().get(projection);
            if (projectionConfig == null) {
                throw new CliError("Could not find projection " + projection );

            } else if (!projectionConfig.getPackaging().isPresent()){
                throw new CliError("Could not find packaging config for projection " + projection );
            }
            return MapUtils.of(projection, projectionConfig.getPackaging().get());
        }
        Map<String, PackagingConfig> packaging = new HashMap<>();
        if (buildConfig.getPackaging().isPresent()) {
            packaging.put("source", buildConfig.getPackaging().get());
        }
        for (Map.Entry<String, ProjectionConfig> projectionConfigEntry : buildConfig.getProjections().entrySet()) {
            if (projectionConfigEntry.getValue().getPackaging().isPresent()) {
                packaging.put(projectionConfigEntry.getKey(),
                        projectionConfigEntry.getValue().getPackaging().get());
            } else {
                LOGGER.info("No packaging config found for projection " + projectionConfigEntry.getKey()
                        + ". Skipping.");
            }
        }
        return packaging;
    }
}

package software.amazon.smithy.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import software.amazon.smithy.build.model.PackagingConfig;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.cli.Arguments;
import software.amazon.smithy.cli.CliError;
import software.amazon.smithy.cli.ColorBuffer;
import software.amazon.smithy.cli.ColorTheme;
import software.amazon.smithy.cli.Command;
import software.amazon.smithy.cli.StandardOptions;
import software.amazon.smithy.cli.dependencies.DependencyResolverException;

public class PublishCommand implements Command {
    private static final Logger LOGGER = Logger.getLogger(PackageCommand.class.getName());
    private final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    private RepositorySystem repositorySystem;

    private final String parentCommandName;

    PublishCommand(String parentCommandName) {
        this.parentCommandName = parentCommandName;
    }

    @Override
    public String getName() {
        return "publish";
    }

    @Override
    public String getSummary() {
        return "Publishes a packaged model to a local maven repo";
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
        SmithyBuildConfig smithyBuildConfig = configOptions.createSmithyBuildConfig();
        StandardOptions standardOptions = arguments.getReceiver(StandardOptions.class);
        PackagingOptions packagingOptions = arguments.getReceiver(PackagingOptions.class);

        if (!standardOptions.quiet()) {
            env.colors().println(env.stderr(), "Publishing artifacts...", ColorTheme.MUTED);
            env.stderr().println("");
        }

        getStuff(null);

        Path outputDirectory = buildOptions.resolveOutput(smithyBuildConfig);
        Path packagedPath = outputDirectory.resolve(packagingOptions.projection())
                .resolve("release").resolve("jar");
        if (!Files.exists(packagedPath)) {
            throw new CliError("Could not find packaged artifacts to publish. Please run the `package` command");
        }

        PackagingConfig config = smithyBuildConfig.getPackaging().get();
        install(config, packagedPath);

        // Publish summary
        try (ColorBuffer buffer = ColorBuffer.of(env.colors(), env.stderr())) {
            if (!standardOptions.quiet()) {
                int remainingLength = 80 - 6 - packagingOptions.projection().length();
                buffer.style(w -> {
                    w.append("──  ");
                    w.append(packagingOptions.projection());
                    w.append("  ");
                    for (int i = 0; i < remainingLength; i++) {
                        w.append("─");
                    }
                    w.println();
                }, ColorTheme.LITERAL);
                buffer.print("successfully published artifacts for projection ")
                        .append(packagingOptions.projection());
                buffer.println();
                buffer.println();
                buffer.println("artifacts published to local maven cache.");
            }
        }

        return 0;
    }

    private void install(PackagingConfig config, Path packagedPath) {
        List<Artifact> artifacts = new ArrayList<>();
        for (Path artifactPath : ConfigurationUtils.listContents(packagedPath.toFile())) {
            Artifact artifact;
            if (artifactPath.toString().contains("-sources")) {
                artifact = new DefaultArtifact(
                        config.getGroupId().get(),
                        config.getArtifactId().get(),
                        "sources",
                        getFileExtension(artifactPath),
                        config.getVersion().get()
                ).setFile(artifactPath.toFile());
            } else {
                artifact = new DefaultArtifact(
                        config.getGroupId().get(),
                        config.getArtifactId().get(),
                        getFileExtension(artifactPath),
                        config.getVersion().get()
                ).setFile(artifactPath.toFile());
            }
            artifacts.add(artifact);
        }
        installArtifacts(artifacts);
    }

    private static String getFileExtension(Path path) {
        return path.toFile().getName().substring(path.toFile().getName().lastIndexOf(".") + 1);
    }

    // TODO: this should all be pulled out into a Publisher interface and associated class
    private void getStuff(String cacheLocation) {
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                throw new DependencyResolverException(exception);
            }
        });

        repositorySystem = locator.getService(RepositorySystem.class);

        // Sets a default maven local to the default local repo of the user.
        if (cacheLocation == null) {
            String userHome = System.getProperty("user.home");
            cacheLocation = Paths.get(userHome, ".m2", "repository").toString();
            LOGGER.fine("Set default Maven local cache location to ~/.m2/repository");
        }

        LocalRepository local = new LocalRepository(cacheLocation);
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, local));
    }

    public void installArtifacts(List<Artifact> artifacts) {
        InstallRequest installRequest = new InstallRequest();
        for (Artifact artifact : artifacts) {
            installRequest.addArtifact(artifact);
        }
        try {
            repositorySystem.install(session, installRequest);
        } catch (InstallationException e) {
            throw new DependencyResolverException(e);
        }
    }
}

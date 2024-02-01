package software.amazon.smithy.cli.commands;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import software.amazon.smithy.cli.Command;
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

        CommandAction action = HelpActionWrapper.fromCommand(this, parentCommandName, this::run);

        return action.apply(arguments, env);
    }

    private int run(Arguments arguments, Env env) {
        BuildOptions buildOptions = arguments.getReceiver(BuildOptions.class);
        ConfigOptions configOptions = arguments.getReceiver(ConfigOptions.class);
        SmithyBuildConfig smithyBuildConfig = configOptions.createSmithyBuildConfig();

        Path outputDirectory = buildOptions.resolveOutput(smithyBuildConfig);
        getStuff(null);
        List<Artifact> artifacts = new ArrayList<>();
        Path packagedPath = outputDirectory.resolve("source").resolve("release")
                .resolve("jar");
        PackagingConfig config = smithyBuildConfig.getPackaging().get();
        for (Path artifactPath : listContents(packagedPath.toFile())) {
            LOGGER.warning("FOUND ARTIFACT: " + artifactPath);
            if (artifactPath.toString().contains("-sources")) {
                Artifact artifact = new DefaultArtifact(
                        config.getGroupId().get(),
                        config.getArtifactId().get(),
                        "sources",
                        getFileExtension(artifactPath),
                        config.getVersion().get()
                ).setFile(artifactPath.toFile());
                artifacts.add(artifact);
            } else {
                Artifact artifact = new DefaultArtifact(
                        config.getGroupId().get(),
                        config.getArtifactId().get(),
                        getFileExtension(artifactPath),
                        config.getVersion().get()
                ).setFile(artifactPath.toFile());
                artifacts.add(artifact);
            }
        }
        LOGGER.warning("INSTALLING ARTIFACTS " + artifacts);
        installArtifacts(artifacts);

        return 0;
    }

    private static String getFileExtension(Path path) {
        return path.toFile().getName().substring(path.toFile().getName().lastIndexOf(".") + 1);
    }

    private static void parseCoordinatesFromPath(Path path) {
        LOGGER.warning("FOUND FILENAME " +  path.toFile().getName());
        LOGGER.warning("FOUND FILE EXTENSION " + getFileExtension(path));

//        String[] parts = coordinates.split(":");
//        if (parts.length != 3) {
//            throw new DependencyResolverException("Invalid Maven coordinates: " + coordinates);
//        }
//        return parts;
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
            InstallResult result = repositorySystem.install(session, installRequest);
            LOGGER.warning("PUBLISHING RESULT " + result);
        } catch (InstallationException e) {
            throw new DependencyResolverException(e);
        }
    }
}

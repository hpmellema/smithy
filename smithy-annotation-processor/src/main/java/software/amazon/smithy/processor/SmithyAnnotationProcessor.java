/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.processor;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import software.amazon.smithy.build.SmithyBuild;
import software.amazon.smithy.build.SmithyBuildResult;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.loader.ModelDiscovery;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.utils.IoUtils;

/**
 * Base implementation of a Smithy annotation processor.
 *
 * <p>This implementation can be extended to create an Annotation processor for
 * a Smithy build plugin that generates Java code. The processor will execute
 * <strong>a single build plugin</strong> and write any generated Java code created
 * by the plugin to the correct output path.
 *
 * <p>Smithy models on the classpath of extending annotation processors are automatically
 * discovered and any java artifacts generated by this plugin are written to standard locations.
 * Generated resource files are written to the class output while all other generated Java
 * classes are written to the source output. Plugin artifacts that are not java files or
 * resource files under {@code META-INF/} are ignored.
 *
 * <p>Extending classes must specify the name of the build plugin they will execute using
 * {@link #getPluginName()}. This plugin must be discoverable on the annotation processor classpath.
 * The plugin is then executed using the data from the annotation as the plugin configuration.
 *
 * <p>For example, if your plugin has the following configuration node in a `smithy-build.json` file:
 * <pre>
 *     "myPlugin" : {
 *         "packageName" = "myPackage.namespace",
 *         "listOfTags" = ["a", "b", "c"]
 *     }
 * </pre>
 * Then you would define an annotation like the following:
 * <pre>
 * {@code
 *  @Target(ElementType.PACKAGE)
 *  public @interface MyPlugin {
 *     String[] listOfTags();
 *  }
 * }
 * </pre>
 * The {@link #createPluginNode} method is used to map this annotation to the object node
 * expected by the plugin. Once a mapping has been defined for the annotation, it can be
 * added to the {@code package-info.java} as follows:
 * <pre>
 * {@code
 * @MyPlugin(listOfTags = {"a", "b", "c"})
 * package com.example.traitcodegen;
 *
 * import com.example.annotations.MyPlugin;
 * }
 * </pre>
 * The base processor class will discover the namespace of the package the annotation
 * is applied to and pass that as a parameter to the {@link #createPluginNode} method.
 *
 * @param <A> Annotation to execute the processor for. This annotation should
 *            target the {@code PACKAGE} {@link java.lang.annotation.ElementType} and
 *            should be applied in the {@code package-info.java} file of a package.
 */
public abstract class SmithyAnnotationProcessor<A extends Annotation> extends AbstractProcessor {
    private static final String MANIFEST_PATH = "META-INF/smithy/manifest";
    private Messager messager;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(getAnnotationClass());
        if (elements.size() > 1) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Only one package can have the " + getAnnotationClass() + " annotation.");
        } else if (elements.size() == 1) {
            messager.printMessage(Diagnostic.Kind.NOTE,
                    "Executing processor: " + this.getClass().getSimpleName() + "...");
            String packageName = getPackageName(elements.iterator().next());
            SmithyBuildConfig config = createBuildConfig(getAnnotation(elements), packageName);
            executeSmithyBuild(config).allArtifacts()
                    .filter(path -> path.toString().contains(getPluginName()) && path.toString().contains("source"))
                    .forEach(this::writeArtifact);
        }

        // Always return false to ensure the annotation processor does not claim the annotation.
        return false;
    }

    /**
     * Name of the Smithy build plugin to execute with this annotation processor.
     *
     * @return name of plugin to run.
     */
    protected abstract String getPluginName();

    /**
     * Annotation class for the processor.
     * <p>
     * Each implementation of {@code SmithyProcessor} should have a specific package-scoped annotation
     * used for configuration. {@link #createPluginNode(Annotation)} maps this annotation to the
     * configuration node for the plugin specified by {@link #getPluginName()}.
     *
     * @return class of the annotation used by this processor
     */
    protected abstract Class<A> getAnnotationClass();

    /**
     * Maps annotation data to a plugin configuration node.
     *
     * @param annotation instance of generator annotation to use to create the build config.
     * @return ObjectNode to use as plugin configuration node.
     */
    protected abstract ObjectNode createPluginNode(A annotation, String packageName);

    private SmithyBuildConfig createBuildConfig(A annotation, String packageName) {
        Map<String, ObjectNode> pluginMap = new HashMap<>();
        pluginMap.put(getPluginName(), createPluginNode(annotation, packageName));
        return SmithyBuildConfig.builder().version("1.0").plugins(pluginMap).build();
    }

    private SmithyBuildResult executeSmithyBuild(SmithyBuildConfig config) {
        ModelAssembler assembler = Model.assembler();

        // Discover any models on the annotation processor classpath
        assembler.discoverModels(getClass().getClassLoader());

        // Load any models found in the resources standard location
        ModelDiscovery.findModels(getManifestUrl()).forEach(assembler::addImport);

        SmithyBuild smithyBuild = SmithyBuild.create(getClass().getClassLoader());
        smithyBuild.model(assembler.assemble().unwrap());
        smithyBuild.config(config);

        return smithyBuild.build();
    }

    private void writeArtifact(Path path) {
        String pathStr = path.toString();
        final String outputPath = pathStr.substring(pathStr.lastIndexOf(getPluginName())
                + getPluginName().length() + 1);
        try {
            // Resources are written to the class output
            if (outputPath.startsWith("META-INF")) {
                try (Writer writer = filer
<<<<<<< HEAD
=======
                        .createResource(StandardLocation.CLASS_OUTPUT, "", convertOutputPath(outputPath))
>>>>>>> e13c3301 (Try another method of enforcing path compatibility)
                        .openWriter()
                ) {
                    writer.write(IoUtils.readUtf8File(path));
                }
                // All other Java files are written to the source output
            } else if (outputPath.endsWith(".java")) {
                // The filer needs to use a namespace convention of `.` rather than the default separator for paths.
                String javaPath = outputPath.replace(FileSystems.getDefault().getSeparator(), ".")
                        .substring(0, outputPath.lastIndexOf(".java"));
                try (Writer writer = filer.createSourceFile(javaPath).openWriter()) {
                    writer.write(IoUtils.readUtf8File(path));
                }
            } else {
                // Non-java files generated are ignored.
                messager.printMessage(Diagnostic.Kind.NOTE, "Ignoring generated file: " + outputPath);
            }
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    private A getAnnotation(Set<? extends Element> elements) {
        return elements.stream()
                .findFirst()
                .map(element -> element.getAnnotation(getAnnotationClass()))
                .orElseThrow(() -> new IllegalStateException("No annotation of type "
                        + getAnnotationClass() + " found on element."));
    }

    private URL getManifestUrl() {
        try {
            return filer.getResource(StandardLocation.CLASS_PATH, "", MANIFEST_PATH).toUri().toURL();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // This is necessary to convert windows paths to a valid URI
    private String convertOutputPath(String outputPath) {
        return outputPath.replace(FileSystems.getDefault().getSeparator(), "/");
    }
<<<<<<< HEAD

    private String getPackageName(Element element) {
        if (element instanceof PackageElement) {
            PackageElement packageElement = (PackageElement) element;
            return packageElement.getQualifiedName().toString();
        } else {
            throw new IllegalStateException("Expected Annotation to be applied to package element "
                    + "but found : " + element);
        }
    }
=======
>>>>>>> e13c3301 (Try another method of enforcing path compatibility)
}

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.traitcodegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.TraitDefinition;
import software.amazon.smithy.model.traits.TraitService;
import software.amazon.smithy.traitcodegen.generators.ShapeGenerator;
import software.amazon.smithy.traitcodegen.integrations.TraitCodegenIntegration;
import software.amazon.smithy.traitcodegen.writer.TraitCodegenWriter;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.SmithyBuilder;

/**
 * Orchestration class for Trait code generation.
 *
 * <p>Trait codegen executes the following steps:
 * <ul>
 *     <li>Orchestrator creation - Plugin creates an instance of {@link TraitCodegen}.</li>
 *     <li>Initialization - {@link #initialize()} is called to discover integration, filter out
 *     any shapes with excluded tags, and set up the codegen context.</li>
 *     <li>Execution - {@link #run()} is called to build a list of shapes to generate by pulling
 *     all shapes with the {@link TraitDefinition} trait applied and walking the nested shapes inside
 *     of those trait shapes. Then the {@link ShapeGenerator} is applied to each of the shapes to
 *     generate. Finally, all of the writers created during the shapes generation process are flushed.</li>
 * </ul>
 *
 */
final class TraitCodegen {
    private static final Logger LOGGER = Logger.getLogger(TraitCodegen.class.getName());

    private final Model model;
    private final TraitCodegenSettings settings;
    private final FileManifest fileManifest;

    private List<TraitCodegenIntegration> integrations;
    private TraitCodegenContext codegenContext;

    private TraitCodegen(Model model,
                         TraitCodegenSettings settings,
                         FileManifest fileManifest
    ) {
        this.model = Objects.requireNonNull(model);
        this.settings = Objects.requireNonNull(settings);
        this.fileManifest = Objects.requireNonNull(fileManifest);
    }

    public static TraitCodegen fromPluginContext(PluginContext context) {
        return new TraitCodegen(
                context.getModel(),
                TraitCodegenSettings.fromNode(context.getSettings()),
                context.getFileManifest()
        );
    }

    public void initialize() {
        LOGGER.info("Initializing trait codegen plugin.");
        integrations = getIntegrations();
        SymbolProvider symbolProvider = createSymbolProvider();
        codegenContext = new TraitCodegenContext(model, settings, symbolProvider, fileManifest, integrations);
        registerInterceptors(codegenContext);
        LOGGER.info("Trait codegen plugin Initialized.");
    }


    public void run() {
        // Check that all required fields have been correctly initialized.
        SmithyBuilder.requiredState("integrations", integrations);
        SmithyBuilder.requiredState("context",  codegenContext);

        // Find all trait definition shapes excluding traits in the prelude.
        LOGGER.info("Generating trait classes.");
        Set<Shape> traitClosure = getTraitClosure(model);
        for (Shape trait : traitClosure) {
            new ShapeGenerator().accept(new GenerateTraitDirective(codegenContext, trait));
        }

        LOGGER.info("Flushing writers");
        // Flush all writers
        if (!codegenContext.writerDelegator().getWriters().isEmpty()) {
            codegenContext.writerDelegator().flushWriters();
        }
    }

    private List<TraitCodegenIntegration> getIntegrations() {
        LOGGER.fine(() -> String.format("Finding integrations using the %s class loader", getClass().getSimpleName()));
        return SmithyIntegration.sort(ServiceLoader.load(TraitCodegenIntegration.class, getClass().getClassLoader()));
    }

    private SymbolProvider createSymbolProvider() {
        SymbolProvider provider = new TraitCodegenSymbolProvider(settings, model);
        for (TraitCodegenIntegration integration : integrations) {
            provider = integration.decorateSymbolProvider(model, settings, provider);
        }
        return SymbolProvider.cache(provider);
    }

    private void registerInterceptors(TraitCodegenContext context) {
        List<CodeInterceptor<? extends CodeSection, TraitCodegenWriter>> interceptors = new ArrayList<>();
        for (TraitCodegenIntegration integration : integrations) {
            interceptors.addAll(integration.interceptors(context));
        }
        context.writerDelegator().setInterceptors(interceptors);
    }

    private Set<Shape> getTraitClosure(Model model) {
        // Get a map of existing providers, so we do not generate any trait definitions
        // for traits pulled in from dependencies.
        Set<ShapeId> existingProviders = new HashSet<>();
        ServiceLoader.load(TraitService.class, TraitCodegen.class.getClassLoader())
                .forEach(service -> existingProviders.add(service.getShapeId()));

        // Get all trait shapes, but filter out prelude shapes
        // and any trait shapes for which a provider is already defined
        Set<Shape> traitClosure = model.getShapesWithTrait(TraitDefinition.class).stream()
                .filter(shape -> !Prelude.isPreludeShape(shape))
                .filter(shape -> !existingProviders.contains(shape.getId()))
                .filter(shape -> !this.hasExcludeTag(shape))
                .collect(Collectors.toSet());

        // Find all shapes connected to trait shapes and therefore within generation closure.
        Set<Shape> nested = new HashSet<>();
        Walker walker = new Walker(model);
        for (Shape traitShape : traitClosure) {
            nested.addAll(walker.walkShapes(traitShape).stream()
                    .filter(shape -> !Prelude.isPreludeShape(shape))
                    .collect(Collectors.toSet()));
        }
        traitClosure.addAll(nested);

        return traitClosure;
    }

    private boolean hasExcludeTag(Shape shape) {
        return shape.getTags().stream().anyMatch(t -> settings.excludeTags().contains(t));
    }
}
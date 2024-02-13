/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.traitcodegen.generators;

import java.util.Iterator;
import java.util.Optional;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.TraitDefinition;
import software.amazon.smithy.traitcodegen.SymbolProperties;
import software.amazon.smithy.traitcodegen.sections.BuilderClassSection;
import software.amazon.smithy.traitcodegen.sections.ToBuilderSection;
import software.amazon.smithy.traitcodegen.writer.TraitCodegenWriter;
import software.amazon.smithy.utils.BuilderRef;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.StringUtils;
import software.amazon.smithy.utils.ToSmithyBuilder;

/**
 * Generates a static builder for a Java class.
 *
 * <p>In addition to the static builder class, this generator will create
 * {@code builder()} and {@code toBuilder()} methods for the target class.
 */
final class BuilderGenerator implements Runnable {
    private static final String VALUES = "values";
    private static final String ACCESSOR_TEMPLATE = "public Builder $1L($2B $1L) {";
    private static final String RETURN_THIS = "return this;";
    private static final String BUILDER_REF_TEMPLATE = "private final BuilderRef<$B> $L = BuilderRef.$L;";
    private static final String BUILDER_METHOD_TEMPLATE = "public static Builder builder() {";

    private final TraitCodegenWriter writer;
    private final Symbol symbol;
    private final SymbolProvider symbolProvider;
    private final Shape baseShape;
    private final Model model;

    BuilderGenerator(TraitCodegenWriter writer, Symbol symbol, SymbolProvider symbolProvider, Shape baseShape,
                            Model model) {
        this.writer = writer;
        this.symbol = symbol;
        this.symbolProvider = symbolProvider;
        this.baseShape = baseShape;
        this.model = model;
    }

    @Override
    public void run() {
        writeToBuilderMethod();
        writeBuilderMethod();
        writeBuilderClass();
    }

    private void writeBuilderClass() {
        writer.pushState(new BuilderClassSection(symbol));
        String builderClassTemplate = "public static final class Builder ";
        if (baseShape.hasTrait(TraitDefinition.class)) {
            writer.addImport(AbstractTraitBuilder.class);
            builderClassTemplate += "extends AbstractTraitBuilder<$T, Builder> {";
        } else {
            writer.addImport(SmithyBuilder.class);
            builderClassTemplate += "implements SmithyBuilder<$T> {";
        }
        writer.openBlock(builderClassTemplate, "}", symbol, () -> {
            baseShape.accept(new BuilderPropertyGenerator());
            writer.newLine();
            writer.writeWithNoFormatting("private Builder() {}").newLine();
            baseShape.accept(new BuilderSetterGenerator());
            writer.newLine();
            writer.override();
            writer.openBlock("public $T build() {", "}", symbol,
                    () -> writer.write("return new $T(this);", symbol));
        });
        writer.popState();
        writer.newLine();
    }

    private void writeToBuilderMethod() {
        writer.pushState(new ToBuilderSection(symbol));
        writer.addImports(SmithyBuilder.class, ToSmithyBuilder.class);
        writer.override();
        writer.openBlock("public SmithyBuilder<$T> toBuilder() {", "}", symbol, () -> {
            writer.writeInlineWithNoFormatting("return builder()");
            writer.indent();
            if (baseShape.hasTrait(TraitDefinition.class)) {
                writer.writeInlineWithNoFormatting(".sourceLocation(getSourceLocation())");
            }
            if (baseShape.members().isEmpty()) {
                writer.writeInlineWithNoFormatting(";");
            }
            writer.newLine();
            // Set all builder properties for any members in the shape
            Iterator<MemberShape> memberIterator = baseShape.members().iterator();
            while (memberIterator.hasNext()) {
                MemberShape member = memberIterator.next();
                writer.writeInline(".$1L($1L)", symbolProvider.toMemberName(member));
                if (memberIterator.hasNext()) {
                    writer.writeWithNoFormatting("\n");
                } else {
                    writer.writeWithNoFormatting(";\n");
                }
            }
            writer.dedent();
        });
        writer.popState();
        writer.newLine();
    }

    private void writeBuilderMethod() {
        writer.openBlock(BUILDER_METHOD_TEMPLATE, "}", () -> writer.write("return new Builder();"));
        writer.newLine();
    }

    private final class BuilderPropertyGenerator extends ShapeVisitor.Default<Void> {

        @Override
        protected Void getDefault(Shape shape) {
            throw new UnsupportedOperationException("Does not support shape of type: " + shape.getType());
        }

        @Override
        public Void listShape(ListShape shape) {
            writeValuesProperty(shape);
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            writeValuesProperty(shape);
            return null;
        }

        @Override
        public Void structureShape(StructureShape shape) {
            shape.members().forEach(this::writeProperty);
            return null;
        }

        private void writeProperty(MemberShape shape) {
            Optional<String> builderRefOptional =
                    symbolProvider.toSymbol(shape).getProperty(SymbolProperties.BUILDER_REF_INITIALIZER, String.class);
            if (builderRefOptional.isPresent()) {
                writer.addImport(BuilderRef.class);
                writer.write(BUILDER_REF_TEMPLATE, symbolProvider.toSymbol(shape),
                        symbolProvider.toMemberName(shape),
                        builderRefOptional.orElseThrow(RuntimeException::new));
            } else {
                writer.write("private $B $L;", symbolProvider.toSymbol(shape),
                        symbolProvider.toMemberName(shape));
            }
        }

        private void writeValuesProperty(Shape shape) {
            Symbol collectionSymbol = symbolProvider.toSymbol(shape);
            writer.addImport(BuilderRef.class);
            writer.write(BUILDER_REF_TEMPLATE, collectionSymbol, VALUES,
                    collectionSymbol.expectProperty(SymbolProperties.BUILDER_REF_INITIALIZER));
        }
    }


    private final class BuilderSetterGenerator extends ShapeVisitor.Default<Void> {
        @Override
        protected Void getDefault(Shape shape) {
            throw new UnsupportedOperationException("Does not support shape of type: " + shape.getType());
        }

        @Override
        public Void listShape(ListShape shape) {
            shape.accept(new SetterVisitor(VALUES));
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            shape.accept(new SetterVisitor(VALUES));
            return null;
        }

        @Override
        public Void structureShape(StructureShape shape) {
            shape.members().forEach(
                    memberShape -> memberShape.accept(new SetterVisitor(symbolProvider.toMemberName(memberShape))));
            writer.newLine();
            return null;
        }
    }


    private final class SetterVisitor extends ShapeVisitor.Default<Void> {
        private final String memberName;

        private SetterVisitor(String memberName) {
            this.memberName = memberName;
        }

        @Override
        protected Void getDefault(Shape shape) {
            writer.openBlock(ACCESSOR_TEMPLATE, "}",
                    memberName, symbolProvider.toSymbol(shape), () -> {
                        writer.write("this.$1L = $1L;", memberName);
                        writer.writeWithNoFormatting(RETURN_THIS);
                    });
            writer.newLine();
            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            writer.openBlock(ACCESSOR_TEMPLATE, "}",
                    memberName, symbolProvider.toSymbol(shape), () -> {
                        writer.write("clear$L();", StringUtils.capitalize(memberName));
                        writer.write("this.$1L.get().addAll($1L);", memberName);
                        writer.writeWithNoFormatting(RETURN_THIS);
                    });
            writer.newLine();

            // Clear all
            writer.openBlock("public Builder clear$L() {", "}",
                    StringUtils.capitalize(memberName), () -> {
                        writer.write("$L.get().clear();", memberName);
                        writer.writeWithNoFormatting(RETURN_THIS);
                    });
            writer.newLine();

            // Set one
            writer.openBlock("public Builder add$LItem($T $LItem) {", "}",
                    StringUtils.capitalize(memberName), symbolProvider.toSymbol(shape.getMember()), memberName,
                    () -> {
                        writer.write("$1L.get().add($1LItem);", memberName);
                        writer.write(RETURN_THIS);
                    });
            writer.newLine();

            // Remove one
            writer.openBlock("public Builder remove$LItem($T $LItem) {", "}",
                    StringUtils.capitalize(memberName), symbolProvider.toSymbol(shape.getMember()), memberName,
                    () -> {
                        writer.write("$1L.get().remove($1LItem);", memberName);
                        writer.write(RETURN_THIS);
                    });
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            // Set all
            writer.openBlock(ACCESSOR_TEMPLATE, "}",
                    memberName, symbolProvider.toSymbol(shape), () -> {
                        writer.write("clear$L();", StringUtils.capitalize(memberName));
                        writer.write("this.$1L.get().putAll($1L);", memberName);
                        writer.write(RETURN_THIS);
                    });
            writer.newLine();

            // Clear all
            writer.openBlock("public Builder clear$L() {", "}", StringUtils.capitalize(memberName), () -> {
                writer.write("this.$L.get().clear();", memberName);
                writer.write(RETURN_THIS);
            });
            writer.newLine();

            // Set one
            MemberShape keyShape = shape.getKey();
            MemberShape valueShape = shape.getValue();
            writer.openBlock("public Builder put$L($T key, $T value) {", "}",
                    StringUtils.capitalize(memberName), symbolProvider.toSymbol(keyShape),
                    symbolProvider.toSymbol(valueShape), () -> {
                        writer.write("this.$L.get().put(key, value);", memberName);
                        writer.write(RETURN_THIS);
                    });
            writer.newLine();

            // Remove one
            writer.openBlock("public Builder remove$L($T $L) {", "}",
                    StringUtils.capitalize(memberName), symbolProvider.toSymbol(keyShape), memberName, () -> {
                        writer.write("this.$1L.get().remove($1L);", memberName);
                        writer.write(RETURN_THIS);
                    });
            writer.newLine();
            return null;
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return model.expectShape(shape.getTarget()).accept(this);
        }
    }
}
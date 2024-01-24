/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.traitcodegen.generators.base;

import java.util.Iterator;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.ShapeDirective;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.EnumValueTrait;
import software.amazon.smithy.traitcodegen.TraitCodegenContext;
import software.amazon.smithy.traitcodegen.TraitCodegenSettings;
import software.amazon.smithy.traitcodegen.TraitCodegenUtils;
import software.amazon.smithy.traitcodegen.generators.common.FromNodeGenerator;
import software.amazon.smithy.traitcodegen.sections.ClassSection;
import software.amazon.smithy.traitcodegen.sections.EnumVariantSection;
import software.amazon.smithy.traitcodegen.writer.TraitCodegenWriter;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Effectively sealed base class for generating a Java Enum class from a Smithy model.
 * <p>
 * The two public implementations provided by this base class are:
 * <ul>
 *     <li>{@link StringEnumGenerator} - Generates a java enum from a Smithy
 *     {@link software.amazon.smithy.model.shapes.EnumShape}.</li>
 *     <li>{@link IntEnumGenerator} - Generates a java enum from a Smithy
 *     {@link software.amazon.smithy.model.shapes.IntEnumShape}.</li>
 * </ul>
 *
 * @param <D> The ShapeDirective to use when generating the enum class.
 */
@SmithyInternalApi
public abstract class EnumGenerator<D extends ShapeDirective<Shape, TraitCodegenContext, TraitCodegenSettings>>
        implements Consumer<D> {
    private static final String VALUE_FIELD_TEMPLATE = "private final $T value;";

    // Private constructor to make abstract class effectively sealed.
    private EnumGenerator() {}

    @Override
    public void accept(D directive) {
        directive.context().writerDelegator().useShapeWriter(directive.shape(),
                writer -> writeEnum(directive.shape(), directive.symbolProvider(), writer, directive.model()));
    }

    protected void writeEnum(Shape enumShape, SymbolProvider provider, TraitCodegenWriter writer, Model model) {
        Symbol enumSymbol = provider.toSymbol(enumShape);
        writer.pushState(new ClassSection(enumShape))
                .openBlock("public enum $L {", "}", enumSymbol.getName(), () -> {
                    writeVariants(enumShape, provider, writer);
                    writer.newLine();

                    writeValueField(writer);
                    writer.newLine();

                    writeConstructor(enumSymbol, writer);

                    writeValueGetter(writer);
                    writer.newLine();

                    new FromNodeGenerator(writer, enumSymbol, enumShape, provider, model).run();
                })
                .popState();
    }

    abstract String getVariantTemplate();

    abstract Symbol getValueType();

    abstract Object getEnumValue(MemberShape member);

    private void writeVariants(Shape enumShape, SymbolProvider provider, TraitCodegenWriter writer) {
        Iterator<MemberShape> memberIterator = enumShape.members().iterator();
        String template = getVariantTemplate();
        while (memberIterator.hasNext()) {
            MemberShape member = memberIterator.next();
            String name = provider.toMemberName(member);
            writer.pushState(new EnumVariantSection(member));
            if (memberIterator.hasNext()) {
                writer.write(template + ",", name, getEnumValue(member));
            } else {
                writer.write(template + ";", name, getEnumValue(member));
            }
            writer.popState();
        }
    }

    private void writeValueField(TraitCodegenWriter writer) {
        writer.write(VALUE_FIELD_TEMPLATE, getValueType());
    }

    private void writeValueGetter(TraitCodegenWriter writer) {
        writer.openBlock("public $T getValue() {", "}", getValueType(),
                () -> writer.write("return value;"));
    }

    private void writeConstructor(Symbol enumSymbol, TraitCodegenWriter writer) {
        writer.openBlock("$T($T value) {", "}",
                enumSymbol, getValueType(), () -> writer.write("this.value = value;"));
        writer.newLine();
    }

    /**
     * Generates a Java Enum class from a smithy {@link software.amazon.smithy.model.shapes.EnumShape}.
     */
    public static final class StringEnumGenerator extends EnumGenerator<GenerateEnumDirective<TraitCodegenContext,
            TraitCodegenSettings>> {
        @Override
        String getVariantTemplate() {
            return "$L($S)";
        }

        @Override
        Symbol getValueType() {
            return TraitCodegenUtils.fromClass(String.class);
        }

        @Override
        Object getEnumValue(MemberShape member) {
            return member.expectTrait(EnumValueTrait.class).expectStringValue();
        }
    }

    /**
     * Generates a Java Enum class from a smithy {@link software.amazon.smithy.model.shapes.IntEnumShape}.
     */
    public static final class IntEnumGenerator extends EnumGenerator<GenerateIntEnumDirective<TraitCodegenContext,
            TraitCodegenSettings>> {
        @Override
        String getVariantTemplate() {
            return "$L($L)";
        }

        @Override
        Symbol getValueType() {
            return TraitCodegenUtils.fromClass(int.class);
        }

        @Override
        Object getEnumValue(MemberShape member) {
            return member.expectTrait(EnumValueTrait.class).expectIntValue();
        }
    }
}

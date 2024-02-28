/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.traitcodegen;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.ListUtils;

final class TraitCodegenSymbolProvider extends ShapeVisitor.DataShapeVisitor<Symbol> implements SymbolProvider {
    private static final String LIST_INITIALIZER = "forList()";
    private static final String MAP_INITIALIZER = "forOrderedMap()";
    private static final List<String> DELIMITERS = ListUtils.of("_", " ", "-");

    private final String packageNamespace;
    private final String smithyNamespace;
    private final Model model;

    TraitCodegenSymbolProvider(TraitCodegenSettings settings, Model model) {
        this.packageNamespace = settings.packageName();
        this.smithyNamespace = settings.smithyNamespace();
        this.model = model;
    }

    @Override
    public Symbol blobShape(BlobShape shape) {
        return TraitCodegenUtils.fromClass(ByteBuffer.class);
    }

    @Override
    public Symbol booleanShape(BooleanShape shape) {
        return TraitCodegenUtils.fromClass(Boolean.class);
    }

    @Override
    public Symbol byteShape(ByteShape shape) {
        return TraitCodegenUtils.fromClass(Byte.class);
    }

    @Override
    public Symbol shortShape(ShortShape shape) {
        return TraitCodegenUtils.fromClass(Short.class);
    }

    @Override
    public Symbol integerShape(IntegerShape shape) {
        return TraitCodegenUtils.fromClass(Integer.class);
    }

    @Override
    public Symbol intEnumShape(IntEnumShape shape) {
        return getJavaClassSymbol(shape);
    }

    @Override
    public Symbol longShape(LongShape shape) {
        return TraitCodegenUtils.fromClass(Long.class);
    }

    @Override
    public Symbol floatShape(FloatShape shape) {
        return TraitCodegenUtils.fromClass(Float.class);
    }

    @Override
    public Symbol doubleShape(DoubleShape shape) {
        return TraitCodegenUtils.fromClass(Double.class);
    }

    @Override
    public Symbol bigIntegerShape(BigIntegerShape shape) {
        return TraitCodegenUtils.fromClass(BigInteger.class);
    }

    @Override
    public Symbol bigDecimalShape(BigDecimalShape shape) {
        return TraitCodegenUtils.fromClass(BigDecimal.class);
    }

    @Override
    public Symbol listShape(ListShape shape) {
        Symbol.Builder builder = TraitCodegenUtils.fromClass(List.class).toBuilder()
                .addReference(toSymbol(shape.getMember()))
                .putProperty(SymbolProperties.BUILDER_REF_INITIALIZER, LIST_INITIALIZER);
        return builder.build();
    }

    @Override
    public Symbol mapShape(MapShape shape) {
        return TraitCodegenUtils.fromClass(Map.class).toBuilder()
                .addReference(shape.getKey().accept(this))
                .addReference(shape.getValue().accept(this))
                .putProperty(SymbolProperties.BUILDER_REF_INITIALIZER, MAP_INITIALIZER)
                .build();
    }

    @Override
    public Symbol stringShape(StringShape shape) {
        return TraitCodegenUtils.fromClass(String.class);
    }

    @Override
    public Symbol enumShape(EnumShape shape) {
        return getJavaClassSymbol(shape);
    }

    @Override
    public Symbol structureShape(StructureShape shape) {
        return getJavaClassSymbol(shape);
    }

    @Override
    public Symbol documentShape(DocumentShape shape) {
        return TraitCodegenUtils.fromClass(ObjectNode.class);
    }

    @Override
    public Symbol memberShape(MemberShape shape) {
        return toSymbol(model.expectShape(shape.getTarget()));
    }

    @Override
    public Symbol timestampShape(TimestampShape shape) {
        throw new UnsupportedOperationException("Union shapes are not supported at this time.");
    }

    @Override
    public Symbol unionShape(UnionShape shape) {
        throw new UnsupportedOperationException("Union shapes are not supported at this time.");
    }

    @Override
    public Symbol toSymbol(Shape shape) {
        return shape.accept(this);
    }

    @Override
    public String toMemberName(MemberShape member) {
        Shape containerShape = model.expectShape(member.getContainer());
        // If the container is a Map list then we assign a simple "values" holder for the collection
        if (containerShape.isMapShape() || containerShape.isListShape()) {
            return "values";
        // Enum shapes should have upper snake case members
        } else if (containerShape.isEnumShape() || containerShape.isIntEnumShape()) {
            return CaseUtils.toSnakeCase(TraitCodegenUtils.MEMBER_ESCAPER.escape(member.getMemberName()))
                    .toUpperCase(Locale.ROOT);
        }

        if (DELIMITERS.stream().anyMatch(member.getMemberName()::contains)) {
            return TraitCodegenUtils.MEMBER_ESCAPER.escape(CaseUtils.toCamelCase(member.getMemberName()));
        } else {
            return TraitCodegenUtils.MEMBER_ESCAPER.escape(member.getMemberName());
        }
    }

    private Symbol getJavaClassSymbol(Shape shape) {
        String name = TraitCodegenUtils.getDefaultName(shape);
        String namespace = TraitCodegenUtils.mapNamespace(smithyNamespace,
                shape.getId().getNamespace(), packageNamespace);
        return Symbol.builder().name(name)
                .namespace(namespace, ".")
                .declarationFile("./" + namespace.replace(".", "/") + "/"  + name + ".java")
                .build();
    }
}

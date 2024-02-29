package software.amazon.smithy.traitcodegen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.example.traits.BaseTimestampTrait;
import com.example.traits.BasicAnnotationTrait;
import com.example.traits.DateTimeTimestampTrait;
import com.example.traits.EpochSecondsTimestampTrait;
import com.example.traits.HttpCodeBigDecimalTrait;
import com.example.traits.HttpCodeBigIntegerTrait;
import com.example.traits.HttpCodeByteTrait;
import com.example.traits.HttpCodeDoubleTrait;
import com.example.traits.HttpCodeFloatTrait;
import com.example.traits.HttpCodeIntegerTrait;
import com.example.traits.HttpCodeLongTrait;
import com.example.traits.HttpCodeShortTrait;
import com.example.traits.HttpDateTimestampTrait;
import com.example.traits.IdRefListTrait;
import com.example.traits.IdRefMapTrait;
import com.example.traits.IdRefStringTrait;
import com.example.traits.IdRefStructTrait;
import com.example.traits.IdRefStructWithNestedIdsTrait;
import com.example.traits.JsonMetadataTrait;
import com.example.traits.ListMember;
import com.example.traits.ListMemberWithMixin;
import com.example.traits.MapValue;
import com.example.traits.NestedA;
import com.example.traits.NestedB;
import com.example.traits.NestedIdRefHolder;
import com.example.traits.NumberListTrait;
import com.example.traits.NumberSetTrait;
import com.example.traits.ResponseTypeIntTrait;
import com.example.traits.ResponseTypeTrait;
import com.example.traits.StringListTrait;
import com.example.traits.StringSetTrait;
import com.example.traits.StringStringMapTrait;
import com.example.traits.StringToStructMapTrait;
import com.example.traits.StringTrait;
import com.example.traits.StructWithMixinTrait;
import com.example.traits.StructWithNestedDocumentTrait;
import com.example.traits.StructWithNestedTimestampsTrait;
import com.example.traits.StructureListTrait;
import com.example.traits.StructureListWithMixinMemberTrait;
import com.example.traits.StructureSetTrait;
import com.example.traits.StructureTrait;
import com.example.traits.SuitTrait;
import com.example.traits.names.SnakeCaseStructureTrait;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.ListUtils;
import software.amazon.smithy.utils.MapUtils;
import software.amazon.smithy.utils.SetUtils;

public class LoadsFromModelTest {
    private static final ShapeId ID = ShapeId.from("test.smithy.traitcodegen#myStruct");
    private static final ShapeId TARGET_ONE = ShapeId.from("test.smithy.traitcodegen#IdRefTarget1");
    private static final ShapeId TARGET_TWO = ShapeId.from("test.smithy.traitcodegen#IdRefTarget2");


    static Stream<Arguments> loadsModelTests() {
        return Stream.of(
                Arguments.of("annotation-trait.smithy", BasicAnnotationTrait.class,
                        Collections.emptyMap()),
                Arguments.of("big-decimal-trait.smithy", HttpCodeBigDecimalTrait.class,
                        MapUtils.of("getValue", new BigDecimal("100.01"))),
                Arguments.of("big-integer-trait.smithy", HttpCodeBigIntegerTrait.class,
                        MapUtils.of("getValue", new BigInteger("100"))),
                Arguments.of("byte-trait.smithy", HttpCodeByteTrait.class,
                        MapUtils.of("getValue", (byte) 1)),
                Arguments.of("document-trait.smithy", JsonMetadataTrait.class,
                        MapUtils.of("toNode", Node.objectNodeBuilder()
                                .withMember("metadata", "woo")
                                .withMember("more", "yay")
                                .build())),
                Arguments.of("double-trait.smithy", HttpCodeDoubleTrait.class,
                        MapUtils.of("getValue", 1.1)),
                Arguments.of("enum-trait.smithy", ResponseTypeTrait.class,
                        MapUtils.of("getValue", "yes", "getEnumValue", ResponseTypeTrait.ResponseType.YES)),
                Arguments.of("float-trait.smithy", HttpCodeFloatTrait.class,
                        MapUtils.of("getValue", 1.1F)),
                Arguments.of("id-ref.smithy", IdRefStringTrait.class,
                        MapUtils.of("getValue", TARGET_ONE)),
                Arguments.of("id-ref.smithy", IdRefListTrait.class,
                        MapUtils.of("getValues", ListUtils.of(TARGET_ONE, TARGET_TWO))),
                Arguments.of("id-ref.smithy", IdRefMapTrait.class,
                        MapUtils.of("getValues", MapUtils.of("a", TARGET_ONE, "b", TARGET_TWO))),
                Arguments.of("id-ref.smithy", IdRefStructTrait.class,
                        MapUtils.of("getFieldA", Optional.of(TARGET_ONE))),
                Arguments.of("id-ref.smithy", IdRefStructWithNestedIdsTrait.class,
                        MapUtils.of("getIdRefHolder", NestedIdRefHolder.builder().id(TARGET_ONE).build(),
                                "getIdList", Optional.of(ListUtils.of(TARGET_ONE, TARGET_TWO)),
                                "getIdMap", Optional.of(MapUtils.of("a", TARGET_ONE, "b", TARGET_TWO)))),
                Arguments.of("integer-trait.smithy", HttpCodeIntegerTrait.class,
                        MapUtils.of("getValue", 1)),
                Arguments.of("long-trait.smithy", HttpCodeLongTrait.class,
                        MapUtils.of("getValue", 1L)),
                Arguments.of("number-list-trait.smithy", NumberListTrait.class,
                        MapUtils.of("getValues", ListUtils.of(1, 2, 3, 4, 5))),
                Arguments.of("number-set-trait.smithy", NumberSetTrait.class,
                        MapUtils.of("getValues", SetUtils.of(1, 2, 3, 4))),
                Arguments.of("short-trait.smithy", HttpCodeShortTrait.class,
                        MapUtils.of("getValue", (short) 1)),
                Arguments.of("int-enum-trait.smithy", ResponseTypeIntTrait.class,
                        MapUtils.of("getValue", 1, "getEnumValue", ResponseTypeIntTrait.ResponseTypeInt.YES)),
                Arguments.of("string-list-trait.smithy", StringListTrait.class,
                        MapUtils.of("getValues", ListUtils.of("a", "b", "c", "d"))),
                Arguments.of("string-set-trait.smithy", StringSetTrait.class,
                        MapUtils.of("getValues", SetUtils.of("a", "b", "c", "d"))),
                Arguments.of("structure-list-trait.smithy", StructureListTrait.class,
                        MapUtils.of("getValues", ListUtils.of(
                                        ListMember.builder().a("first").b(1).c("other").build(),
                                        ListMember.builder().a("second").b(2).c("more").build()))),
                Arguments.of("string-trait.smithy", StringTrait.class,
                        MapUtils.of("getValue","Testing String Trait")),
                Arguments.of("structure-set-trait.smithy", StructureSetTrait.class,
                        MapUtils.of("getValues", ListUtils.of(
                                ListMember.builder().a("first").b(1).c("other").build(),
                                ListMember.builder().a("second").b(2).c("more").build()))),
                Arguments.of("string-string-map-trait.smithy", StringStringMapTrait.class,
                        MapUtils.of("getValues", MapUtils.of("a", "stuff",
                                        "b", "other", "c", "more!"))),
                Arguments.of("string-struct-map-trait.smithy", StringToStructMapTrait.class,
                        MapUtils.of("getValues", MapUtils.of(
                                "one", MapValue.builder().a("foo").b(2).build(),
                                "two", MapValue.builder().a("bar").b(4).build()))),
                Arguments.of("struct-trait.smithy", StructureTrait.class,
                        MapUtils.of(
                                "getFieldA", "first",
                                "getFieldB", Optional.of(false),
                                "getFieldC", Optional.of(NestedA.builder()
                                        .fieldN("nested")
                                        .fieldQ(true)
                                        .fieldZ(NestedB.A)
                                        .build()),
                                "getFieldD", Optional.of(ListUtils.of("a", "b", "c")),
                                "getFieldE", Optional.of(MapUtils.of("a", "one", "b", "two")),
                                "getFieldF", Optional.of(new BigDecimal("100.01")),
                                "getFieldG", Optional.of(new BigInteger("100")))),
                Arguments.of("snake-case-struct.smithy", SnakeCaseStructureTrait.class,
                        MapUtils.of("getSnakeCaseMember", Optional.of("stuff"))),
                Arguments.of("struct-list-with-mixin-trait.smithy", StructureListWithMixinMemberTrait.class,
                        MapUtils.of("getValues", ListUtils.of(
                                        ListMemberWithMixin.builder().a("first").b(1).c("other")
                                                .d("mixed-in").build(),
                                        ListMemberWithMixin.builder().a("second").b(2).c("more")
                                                .d("mixins are cool").build()))),
                Arguments.of("struct-with-mixin-trait.smithy", StructWithMixinTrait.class,
                        MapUtils.of("getD", "mixed-in")),
                Arguments.of("legacy-enum-trait.smithy", SuitTrait.class,
                        MapUtils.of("getEnumValue", SuitTrait.Suit.CLUB, "getValue", "club")),
                Arguments.of("nested-document-trait.smithy", StructWithNestedDocumentTrait.class,
                        MapUtils.of("getDoc", Optional.of(ObjectNode.builder().withMember("foo", "bar")
                                .withMember("fizz", "buzz").build()))),
                Arguments.of("nested-timestamps.smithy", StructWithNestedTimestampsTrait.class,
                        MapUtils.of("getBaseTime", Instant.parse("1985-04-12T23:20:50.52Z"),
                                    "getDateTime", Instant.parse("1985-04-12T23:20:50.52Z"),
                                "getHttpDate", Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse("Tue, 29 Apr 2014 18:30:38 GMT")),
                                "getEpochSeconds", Instant.ofEpochSecond((long) 1515531081.123))),
                Arguments.of("base-timestamp-trait.smithy", BaseTimestampTrait.class,
                        MapUtils.of("getValue", Instant.parse("1985-04-12T23:20:50.52Z"))),
                Arguments.of("date-time-timestamp-trait.smithy", DateTimeTimestampTrait.class,
                        MapUtils.of("getValue", Instant.parse("1985-04-12T23:20:50.52Z"))),
                Arguments.of("http-date-trait.smithy", HttpDateTimestampTrait.class,
                        MapUtils.of("getValue", Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME
                                        .parse("Tue, 29 Apr 2014 18:30:38 GMT")))),
                Arguments.of("epoch-seconds-timestamp-trait.smithy", EpochSecondsTimestampTrait.class,
                        MapUtils.of("getValue", Instant.ofEpochSecond((long) 1515531081.123)))
        );
    }

    @ParameterizedTest
    @MethodSource("loadsModelTests")
    <T extends Trait> void executeTests(String resourceFile,
                                        Class<T> traitClass,
                                        Map<String, Object> valueChecks
    ) {
        Model result = Model.assembler()
                .discoverModels(getClass().getClassLoader())
                .addImport(Objects.requireNonNull(getClass().getResource(resourceFile)))
                .assemble()
                .unwrap();
        T trait = result.expectShape(ID).expectTrait(traitClass);
        valueChecks.forEach((k, v) -> checkValue(traitClass, trait, k, v));
    }

    <T extends Trait> void checkValue(Class<T> traitClass, T trait, String accessor, Object expected) {
        try {
            Object value = traitClass.getMethod(accessor).invoke(trait);
            // Float values need a delta specified for equals checks
            if (value instanceof Float) {
                assertEquals((Float) expected, (Float) value, 0.0001,
                        "Value of accessor `" + accessor + "` invalid for " + trait);
            } else if (value instanceof Iterable) {
                assertIterableEquals((Iterable<?>) expected, (Iterable<?>) value);
            } else {
                assertEquals(expected, value, "Value of accessor `" + accessor
                        + "` invalid for " + trait);
            }

        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException  e) {
            throw new RuntimeException("Failed to invoke accessor " + accessor + " for " + trait, e);
        }
    }
}

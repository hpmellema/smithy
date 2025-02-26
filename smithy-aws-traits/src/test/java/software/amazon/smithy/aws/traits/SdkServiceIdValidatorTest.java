/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.aws.traits;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.validation.Severity;
import software.amazon.smithy.model.validation.ValidatedResult;

public class SdkServiceIdValidatorTest {
    @Test
    public void validatesServiceShapesDuringBuild() {
        ShapeId id = ShapeId.from("com.foo#Baz");
        ServiceShape serviceShape = ServiceShape.builder()
                .id(id)
                .version("2016-04-01")
                .addTrait(ServiceTrait.builder().sdkId("AWS Foo").build(id))
                .build();
        ValidatedResult<Model> result = Model.assembler()
                .addShape(serviceShape)
                .discoverModels(getClass().getClassLoader())
                .assemble();

        assertThat(result.getValidationEvents(Severity.DANGER), not(empty()));
    }

    @Test
    public void doesNotAllowCompanyNames() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> SdkServiceIdValidator.validateServiceId("AWS Foo"));

        assertThat(thrown.getMessage(), containsString("company names"));
    }

    @Test
    public void doesNotAllowBadSuffix() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> SdkServiceIdValidator.validateServiceId("Foo Service"));

        assertThat(thrown.getMessage(), containsString("case-insensitively end with"));
    }

    @Test
    public void mustMatchRegex() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> SdkServiceIdValidator.validateServiceId("!Nope!"));
    }

    @Test
    public void noTrailingWhitespace() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> SdkServiceIdValidator.validateServiceId("Foo "));
    }

    @Test
    public void doesNotAllowShortIds() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> SdkServiceIdValidator.validateServiceId(""));

        assertThat(thrown.getMessage(), containsString("1 and 50"));
    }

    @Test
    public void doesNotAllowLongIds() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> SdkServiceIdValidator.validateServiceId("Foobarbazqux Foobarbazqux Foobarbazqux Foobarbazqux"));

        assertThat(thrown.getMessage(), containsString("1 and 50"));
    }
}

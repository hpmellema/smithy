/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.traitcodegen.integrations.core;

import java.util.List;
import software.amazon.smithy.traitcodegen.TraitCodegenContext;
import software.amazon.smithy.traitcodegen.integrations.TraitCodegenIntegration;
import software.amazon.smithy.traitcodegen.writer.TraitCodegenWriter;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.ListUtils;

public final class CoreIntegration implements TraitCodegenIntegration  {

    @Override
    public String name() {
        return "core";
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, TraitCodegenWriter>> interceptors(
            TraitCodegenContext codegenContext) {
        return ListUtils.of(
                new GeneratedAnnotationInterceptor(),
                new DeprecatedAnnotationClassInterceptor(),
                new DeprecatedNoteInterceptor(),
                new ClassJavaDocInterceptor(),
                new ExternalDocsInterceptor(),
                new FromNodeDocsInterceptor(),
                new BuilderMethodDocsInterceptor(),
                new BuilderClassSectionDocsInterceptor(),
                new GetterJavaDocInterceptor(),
                new EnumVariantJavaDocInterceptor()
        );
    }
}

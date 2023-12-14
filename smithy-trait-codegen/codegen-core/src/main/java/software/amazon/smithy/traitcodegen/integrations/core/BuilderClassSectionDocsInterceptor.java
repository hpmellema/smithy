/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.traitcodegen.integrations.core;


import software.amazon.smithy.traitcodegen.sections.BuilderClassSection;
import software.amazon.smithy.traitcodegen.writer.TraitCodegenWriter;
import software.amazon.smithy.utils.CodeInterceptor;

final class BuilderClassSectionDocsInterceptor implements CodeInterceptor.Prepender<BuilderClassSection,
        TraitCodegenWriter> {
    @Override
    public void prepend(TraitCodegenWriter writer, BuilderClassSection section) {
        writer.openDocstring();
        writer.writeDocStringContents("Builder for {@link $T}.", section.symbol());
        writer.closeDocstring();
    }

    @Override
    public Class<BuilderClassSection> sectionType() {
        return BuilderClassSection.class;
    }
}

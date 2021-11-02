package io.kestra.core.models.templates;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class TemplateSource extends Template {
    String source;
    String exception;
}

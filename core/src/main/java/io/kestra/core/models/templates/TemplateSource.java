package io.kestra.core.models.templates;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
public class TemplateSource extends Template {
    String source;
    String exception;
}

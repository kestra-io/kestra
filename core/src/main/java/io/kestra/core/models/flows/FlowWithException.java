package io.kestra.core.models.flows;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
@Jacksonized
public class FlowWithException extends FlowWithSource {
    String exception;
}

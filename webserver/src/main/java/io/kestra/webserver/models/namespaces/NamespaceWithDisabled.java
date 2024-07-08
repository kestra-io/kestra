package io.kestra.webserver.models.namespaces;

import io.kestra.core.models.namespaces.Namespace;
import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
public class NamespaceWithDisabled extends Namespace implements DisabledInterface {
    boolean disabled;
}
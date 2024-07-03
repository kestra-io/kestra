package io.kestra.core.models.namespaces;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class Namespace implements NamespaceInterface {
    @NotNull
    @Pattern(regexp="^[a-z0-9][a-z0-9._-]*")
    protected String id;

    @NotNull
    @Builder.Default
    boolean deleted = false;
}

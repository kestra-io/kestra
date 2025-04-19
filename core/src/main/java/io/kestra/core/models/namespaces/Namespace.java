package io.kestra.core.models.namespaces;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "NamespaceLight")
public class Namespace implements NamespaceInterface {
    @NotNull
    @Pattern(regexp="^[a-z0-9][a-z0-9._-]*")
    protected String id;

    @NotNull
    @Builder.Default
    boolean deleted = false;
}

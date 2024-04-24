package io.kestra.core.models.flows;

import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.TenantInterface;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public abstract class AbstractFlow implements DeletedInterface, TenantInterface {
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*")
    String id;

    @NotNull
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    String namespace;

    @Min(value = 1)
    Integer revision;

    @Valid
    List<Input<?>> inputs;

    @NotNull
    @Builder.Default
    boolean disabled = false;

    @Getter
    @NotNull
    @Builder.Default
    boolean deleted = false;

    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String tenantId;

}

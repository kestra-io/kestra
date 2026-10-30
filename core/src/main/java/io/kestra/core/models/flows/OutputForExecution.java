package io.kestra.core.models.flows;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * An {@link Output} as exposed to the execution page: only its declaration, never its value expression.
 */
@SuperBuilder
@NoArgsConstructor
public class OutputForExecution extends Output {
    public static OutputForExecution of(Output output) {
        return OutputForExecution.builder()
            .id(output.getId())
            .description(output.getDescription())
            .type(output.getType())
            .displayName(output.getDisplayName())
            .required(output.getRequired())
            .searchable(output.getSearchable())
            .build();
    }

    /**
     * The OpenAPI shape of an {@link OutputForExecution}, declared apart because a subclass schema would inherit {@link Output}'s required value.
     */
    @Schema(name = "OutputForExecution")
    public record Declaration(
        @NotNull String id,
        String description,
        @NotNull Type type,
        String displayName,
        Boolean required,
        Boolean searchable) {
    }
}

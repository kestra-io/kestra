package io.kestra.core.models.flows;

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
}

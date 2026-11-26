package io.kestra.core.models.dashboards.filters;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.validations.SafeRegexValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Schema(title = "REGEX")
public class Regex<F extends Enum<F>> extends AbstractFilter<F> {
    @NotNull
    @JsonInclude
    @Builder.Default
    @Schema(title = TYPE_TITLE)
    protected FilterType type = FilterType.REGEX;

    @NotNull
    @SafeRegexValidation
    @Schema(title = "Regular expression the field must match.")
    private String value;
}
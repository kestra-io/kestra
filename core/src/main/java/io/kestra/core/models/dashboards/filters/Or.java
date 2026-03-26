package io.kestra.core.models.dashboards.filters;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.validations.OrFilterValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
@OrFilterValidation
@Schema(title = "OR")
public class Or<F extends Enum<F>> extends AbstractFilter<F> {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected FilterType type = FilterType.OR;

    @NotNull
    @NotEmpty
    private List<AbstractFilter<F>> values;
}
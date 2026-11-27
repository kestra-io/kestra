package io.kestra.core.models.dashboards.filters;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

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
@Schema(title = "GREATER_THAN")
public class GreaterThan<F extends Enum<F>> extends AbstractFilter<F> {
    @NotNull
    @JsonInclude
    @Builder.Default
    @Schema(title = TYPE_TITLE)
    protected FilterType type = FilterType.GREATER_THAN;

    @NotNull
    @Schema(anyOf = { Number.class, ZonedDateTime.class }, title = "Value the field must be greater than.")
    private Object value;
}
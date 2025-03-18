package io.kestra.core.models.dashboards.filters;

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
@Schema(title = "REGEX")
public class Regex <F extends Enum<F>> extends AbstractFilter<F> {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected FilterType type = FilterType.REGEX;

    @NotNull
    private String value;
}
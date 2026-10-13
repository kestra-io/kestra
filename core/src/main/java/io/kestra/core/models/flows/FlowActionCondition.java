package io.kestra.core.models.flows;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.models.dashboards.filters.AbstractFilter.FilterType;
import io.kestra.core.utils.RegexUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(title = "A condition the resource an action is attached to must satisfy for the action to be offered.")
public record FlowActionCondition(
    @NotBlank
    @Schema(
        title = "The field of the resource this condition reads.",
        description = "A field such as `status` or `namespace`, or `metadata.<key>` on an asset."
    ) String field,

    @NotNull
    @Schema(title = "How the field is compared to the value.") FilterType operator,

    @Nullable
    @Schema(
        title = "The value the field is compared to.",
        description = "A list for `IN` and `NOT_IN`, ignored by `IS_NULL` and `IS_NOT_NULL`.",
        anyOf = {
            String.class, Double.class, Boolean.class, List.class }
    ) Object value){
    private static final Set<FilterType> UNSUPPORTED_OPERATORS = EnumSet.of(FilterType.OR, FilterType.IS_TRUE, FilterType.IS_FALSE);

    @AssertTrue(message = "operator must not be OR, IS_TRUE or IS_FALSE")
    @JsonIgnore
    public boolean isOperatorSupported() {
        return operator == null || !UNSUPPORTED_OPERATORS.contains(operator);
    }

    @AssertTrue(message = "value is required unless operator is IS_NULL or IS_NOT_NULL")
    @JsonIgnore
    public boolean isValueGivenWhenCompared() {
        return operator == null || FilterType.IS_NULL == operator || FilterType.IS_NOT_NULL == operator
            || (value != null && !"".equals(value));
    }

    @AssertTrue(message = "value must be a non-empty list for IN and NOT_IN, and a single value for the other operators")
    @JsonIgnore
    public boolean isValueShapedForOperator() {
        if (FilterType.IN == operator || FilterType.NOT_IN == operator) {
            return value instanceof List<?> values && !values.isEmpty();
        }
        return !(value instanceof List<?>);
    }

    @AssertTrue(message = "value must be a valid regex pattern, short and free of nested quantifiers, when operator is REGEX")
    @JsonIgnore
    public boolean isRegexSafe() {
        return FilterType.REGEX != operator
            || (value instanceof String regex && RegexUtils.isSafeUserRegex(regex) && RegexUtils.syntaxError(regex).isEmpty());
    }
}

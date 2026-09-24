package io.kestra.core.models.flows;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.models.QueryFilter;
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
    @Schema(title = "How the field is compared to the value.") QueryFilter.Op op,

    @Nullable
    @Schema(
        title = "The value the field is compared to.",
        description = "A list for `IN` and `NOT_IN`, ignored by `IS_NULL` and `IS_NOT_NULL`.",
        anyOf = {
            String.class, Double.class, Boolean.class, List.class }
    ) Object value){
    @AssertTrue(message = "value is required unless op is IS_NULL or IS_NOT_NULL")
    @JsonIgnore
    public boolean isValueGivenWhenCompared() {
        return op == null || QueryFilter.Op.IS_NULL == op || QueryFilter.Op.IS_NOT_NULL == op
            || (value != null && !"".equals(value));
    }

    @AssertTrue(message = "value must be a non-empty list when op is IN or NOT_IN")
    @JsonIgnore
    public boolean isListGivenWhenMatchingAny() {
        return (QueryFilter.Op.IN != op && QueryFilter.Op.NOT_IN != op)
            || (value instanceof List<?> values && !values.isEmpty());
    }

    @AssertTrue(message = "value must be a valid regex pattern, short and free of nested quantifiers, when op is REGEX")
    @JsonIgnore
    public boolean isRegexSafe() {
        return QueryFilter.Op.REGEX != op
            || (value instanceof String regex && RegexUtils.isSafeUserRegex(regex) && RegexUtils.syntaxError(regex).isEmpty());
    }
}

package io.kestra.core.models.assets;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(title = "A condition an asset must satisfy for an action to be offered on it.")
public record AssetActionCondition(
    @NotBlank
    @Schema(
        title = "The asset field this condition reads.",
        description = "A dotted path into the asset, such as `namespace`, `type`, `state` or `metadata.node_count`."
    )
    String field,

    @NotNull
    @Schema(title = "How the field is compared to the value.")
    Operator op,

    @Schema(
        title = "The value the field is compared to.",
        description = "A list for `IN` and `NOT_IN`, ignored by `IS_SET` and `IS_NOT_SET`."
    )
    Object value
) {
    public enum Operator {
        EQ,
        NE,
        IN,
        NOT_IN,
        LT,
        LTE,
        GT,
        GTE,
        STARTS_WITH,
        CONTAINS,
        IS_SET,
        IS_NOT_SET
    }
}

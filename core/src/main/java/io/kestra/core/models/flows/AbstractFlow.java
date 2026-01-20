package io.kestra.core.models.flows;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.models.Label;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonDeserialize
public abstract class AbstractFlow implements FlowInterface {
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*")
    @Size(min = 1, max = 100)
    String id;

    @NotNull
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    @Size(min = 1, max = 150)
    String namespace;

    @Min(value = 1)
    Integer revision;

    String description;

    @Valid
    List<Input<?>> inputs;

    @Valid
    List<Output> outputs;

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

    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    @Schema(
        description = "Labels as a list of Label (key/value pairs) or as a map of string to string.",
        implementation = Object.class,
        oneOf = {
            Label[].class,
            Map.class
        }
    )
    @Valid
    List<Label> labels;

    @Schema(
        type = "object",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
    )
    Map<String, Object> variables;


    @Valid
    private WorkerGroup workerGroup;

}

package io.kestra.core.test.flow;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.models.Label;
import io.kestra.core.serializers.Jackson3ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.Jackson3ListOrMapOfLabelSerializer;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fixtures {

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
    private Map<String, Object> inputs;

    private Map<String, String> files;

    private List<@Valid TaskFixture> tasks;

    @Valid
    private TriggerFixture trigger;

    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    @tools.jackson.databind.annotation.JsonSerialize(using = Jackson3ListOrMapOfLabelSerializer.class)
    @tools.jackson.databind.annotation.JsonDeserialize(using = Jackson3ListOrMapOfLabelDeserializer.class)
    @Schema(
        description = "Labels as a list of Label (key/value pairs) or as a map of string to string.",
        implementation = Object.class,
        oneOf = {
            Label[].class,
            Map.class
        }
    )
    List<@Valid Label> labels;
}

package io.kestra.core.models.triggers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@SuperBuilder(toBuilder = true)
@Getter
@Schema(
    title = "A backfill configuration."
)
@NoArgsConstructor
public class Backfill {
    @Schema(
        title = "The start date."
    )
    @NotNull
    ZonedDateTime start;

    @Schema(
        title = "The end date."
    )
    @NotNull
    ZonedDateTime end;

    @Schema(
        title = "The current date of the backfill being done."
    )
    ZonedDateTime currentDate;

    @Schema(
        title = "If the backfill is paused."
    )
    @Builder.Default
    @JsonInclude
    Boolean paused = false;

    @Schema(
        title = "The input to pass to the backfilled flow."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    @Schema(
        title = "The labels to pass to the backfilled flow."
    )
    List<Label> labels;

    @Schema(
        title = "The nextExecutionDate before the backfill was created."
    )
    ZonedDateTime previousNextExecutionDate;

//    public Backfill(ZonedDateTime start, ZonedDateTime end) {
//        this.start = start;
//        this.end = end;
//        this.currentDate = start;
//    }

    public Backfill(ZonedDateTime start, ZonedDateTime end, ZonedDateTime currentDate, Boolean paused, Map<String, Object> inputs, List<Label> labels, ZonedDateTime previousNextExecutionDate) {
        this.start = start;
        this.end = end;
        this.currentDate = start;
        this.paused = paused != null ? paused : false;
        this.inputs = inputs;
        this.labels = labels;
        this.previousNextExecutionDate = previousNextExecutionDate;
    }

}

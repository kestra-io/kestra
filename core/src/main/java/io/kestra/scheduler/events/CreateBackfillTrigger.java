package io.kestra.scheduler.events;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.models.Label;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.validations.NoSystemLabelValidation;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * A command to backfill a trigger.
 */
public record CreateBackfillTrigger(
    TriggerId id,
    Backfill backfill,
    Instant timestamp
) implements TriggerEvent {
    
    public CreateBackfillTrigger(TriggerId id, Backfill backfill) {
        this(id, backfill, Instant.now());
    }
    
    public record Backfill(
        ZonedDateTime start,
        ZonedDateTime end,
        Map<String, Object> inputs,
        @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
        @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
        List<@NoSystemLabelValidation Label> labels
    ) {
    }
}

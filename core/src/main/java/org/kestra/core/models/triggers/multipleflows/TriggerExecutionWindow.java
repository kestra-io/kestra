package org.kestra.core.models.triggers.multipleflows;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Value
@Builder
public class TriggerExecutionWindow {
    String namespace;
    String flowId;
    String conditionId;
    ZonedDateTime start;
    ZonedDateTime end;
    Map<String, Boolean> results;

    public TriggerExecutionWindow with(Map<String, Boolean> newResult) {
        Map<String, Boolean> finalResults = new HashMap<>(results);

        newResult
            .entrySet()
            .stream()
            .filter(Map.Entry::getValue)
            .forEach(e -> finalResults.put(e.getKey(), true));

        return new TriggerExecutionWindow(
            this.namespace,
            this.flowId,
            this.conditionId,
            this.start,
            this.end,
            finalResults
        );
    }
}

package io.kestra.core.models.triggers.multipleflows;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;
import io.kestra.core.models.flows.Flow;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Value
@Builder
public class MultipleConditionWindow {
    String namespace;

    String flowId;

    String conditionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm[:ss][.SSS]XXX")
    ZonedDateTime start;


    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm[:ss][.SSS]XXX")
    ZonedDateTime end;

    Map<String, Boolean> results;

    @JsonIgnore
    public String uid() {
        return String.join("_", Arrays.asList(
            this.namespace,
            this.flowId,
            this.conditionId
        ));
    }

    public static String uid(Flow flow, String conditionId) {
        return String.join("_", Arrays.asList(
            flow.getNamespace(),
            flow.getId(),
            conditionId
        ));
    }

    public boolean isValid(ZonedDateTime now) {
        return now.isAfter(this.getStart()) && now.isBefore(this.getEnd());
    }

    public MultipleConditionWindow with(Map<String, Boolean> newResult) {
        Map<String, Boolean> finalResults = new HashMap<>();

        if (results != null) {
            finalResults.putAll(results);
        }

        newResult
            .entrySet()
            .stream()
            .filter(Map.Entry::getValue)
            .forEach(e -> finalResults.put(e.getKey(), true));

        return new MultipleConditionWindow(
            this.namespace,
            this.flowId,
            this.conditionId,
            this.start,
            this.end,
            finalResults
        );
    }
}

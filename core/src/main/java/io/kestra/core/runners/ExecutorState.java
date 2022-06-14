package io.kestra.core.runners;

import io.kestra.core.models.flows.State;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
public class ExecutorState {
    private String executionId;
    private Map<String, State.Type> workerTaskDeduplication = new ConcurrentHashMap<>();
    private Map<String, String> childDeduplication = new ConcurrentHashMap<>();
    private Boolean flowTriggerDeduplication = false;

    public ExecutorState(String executionId) {
        this.executionId = executionId;
    }
}

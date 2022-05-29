package io.kestra.jdbc.runner;

import io.kestra.core.models.flows.State;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
public class JdbcExecutorState {
    private String executionId;
    private Map<String, State.Type> workerTaskDeduplication = new ConcurrentHashMap<>();
    private Map<String, String> childDeduplication = new ConcurrentHashMap<>();
    private Boolean flowTriggerDeduplication = false;

    public JdbcExecutorState(String executionId) {
        this.executionId = executionId;
    }
}

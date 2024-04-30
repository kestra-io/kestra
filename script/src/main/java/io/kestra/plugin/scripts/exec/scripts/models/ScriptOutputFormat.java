package io.kestra.plugin.scripts.exec.scripts.models;

import io.kestra.core.models.executions.AbstractMetricEntry;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Data
public class ScriptOutputFormat<T> {
    private Map<String, Object> outputs;
    private List<AbstractMetricEntry<T>> metrics;
}

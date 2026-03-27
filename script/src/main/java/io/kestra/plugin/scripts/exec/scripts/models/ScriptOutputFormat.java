package io.kestra.plugin.scripts.exec.scripts.models;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.executions.AbstractMetricEntry;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ScriptOutputFormat<T> {
    private Map<String, Object> outputs;
    private List<AbstractMetricEntry<T>> metrics;
}

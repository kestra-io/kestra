package io.kestra.core.models.assets;

import io.kestra.core.models.flows.State;

import java.time.Instant;
import java.util.List;

public record AssetLineage(
    String tenantId,
    String namespace,
    String flowId,
    Integer flowRevision,
    String executionId,
    String taskId,
    String taskRunId,
    State.Type state,
    Instant startDate,
    Instant endDate,
    List<Asset> inputs,
    List<Asset> outputs,
    Instant timestamp) { }

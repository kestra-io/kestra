package io.kestra.core.services;

import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

@Singleton
public class SkipExecutionService {
    private volatile List<String> skipExecutions = Collections.emptyList();

    public synchronized void setSkipExecutions(List<String> skipExecutions) {
        this.skipExecutions = skipExecutions;
    }

    public boolean skipExecution(String executionId) {
        return skipExecutions.contains(executionId);
    }
}

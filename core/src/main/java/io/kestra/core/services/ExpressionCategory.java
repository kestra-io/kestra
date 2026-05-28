package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Stable, typed categories for Pebble expression context.
 * <p>
 * Each category has a human-readable {@link #displayName()} used in LLM prompt labels,
 * and a stable JSON {@link #key()} used as the API response map key.
 * Both consumers of the expression context (No-Code editor autocompletion and AI Copilot
 * prompts) must use these constants so that renaming is caught at compile time.
 */
public enum ExpressionCategory {
    TASK_OUTPUTS("Task Outputs", "taskOutputs"),
    EXECUTION_CONTEXT("Execution Context", "executionContext"),
    INPUTS("Inputs", "inputs"),
    VARIABLES("Variables", "variables"),
    SECRETS("Secrets", "secrets"),
    KV_PAIRS("KV Pairs", "kvPairs"),
    NAMESPACE_FILES("Namespace Files", "namespaceFiles"),
    FILTERS("Filters (use as | filterName)", "filters"),
    FUNCTIONS("Functions", "functions"),
    // App-specific categories
    APP_CONTEXT("App Context", "appContext");

    private final String displayName;
    private final String key;

    ExpressionCategory(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }

    /** Human-readable label used in LLM prompt output (e.g. "Task Outputs: ..."). */
    public String displayName() {
        return displayName;
    }

    /** Stable JSON key used in the API response map (e.g. "taskOutputs"). */
    @JsonValue
    public String key() {
        return key;
    }
}

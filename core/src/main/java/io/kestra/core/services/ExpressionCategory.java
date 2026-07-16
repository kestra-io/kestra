package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("taskOutputs")
    TASK_OUTPUTS("Task Outputs", "taskOutputs"),
    @JsonProperty("executionContext")
    EXECUTION_CONTEXT("Execution Context", "executionContext"),
    @JsonProperty("inputs")
    INPUTS("Inputs", "inputs"),
    @JsonProperty("variables")
    VARIABLES("Variables", "variables"),
    @JsonProperty("secrets")
    SECRETS("Secrets", "secrets"),
    @JsonProperty("kvPairs")
    KV_PAIRS("KV Pairs", "kvPairs"),
    @JsonProperty("namespaceFiles")
    NAMESPACE_FILES("Namespace Files", "namespaceFiles"),
    @JsonProperty("filters")
    FILTERS("Filters (use as | filterName)", "filters"),
    @JsonProperty("functions")
    FUNCTIONS("Functions", "functions"),
    // App-specific categories
    @JsonProperty("appContext")
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

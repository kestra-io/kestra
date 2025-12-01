package io.kestra.core.validations;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;

/**
 * Configurable validation limits for flow/task properties.
 * Bind values from application configuration under `kestra.validation.*`.
 */
@ConfigurationProperties("kestra.validation")
@Introspected
@Getter
@Setter
public class ValidationConfiguration {
    /**
     * Maximum allowed concurrency for Flow-level `concurrency.limit`.
     * If null, no upper bound validation is applied.
     */
    private Integer flowMaxConcurrency = 2000;

    /**
     * Maximum allowed concurrency for io.kestra.plugin.core.flow.ForEach `concurrencyLimit`.
     * If null, no upper bound validation is applied.
     */
    private Integer foreachMaxConcurrency = 100;

    /**
     * Maximum allowed value for io.kestra.plugin.core.flow.ForEach `maxValues`.
     * If null, no upper bound validation is applied.
     */
    private Integer foreachMaxValues = 100;

    /**
     * Maximum allowed value for io.kestra.plugin.core.flow.ForEachItem `maxBatches`.
     * If null, no upper bound validation is applied.
     */
    private Integer foreachItemMaxBatches = 100000;
}
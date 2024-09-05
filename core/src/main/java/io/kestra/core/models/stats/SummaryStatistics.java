package io.kestra.core.models.stats;

/**
 * Summary statistic about the usage.
 * @param flows      Total number of flows.
 * @param triggers   Total number of namespace.
 */
public record SummaryStatistics(
    int flows,
    int triggers
) {
}

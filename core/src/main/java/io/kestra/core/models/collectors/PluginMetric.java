package io.kestra.core.models.collectors;

public record PluginMetric(String type, double count, double totalTime, double meanTime){
}

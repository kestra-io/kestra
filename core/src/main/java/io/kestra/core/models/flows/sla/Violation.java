package io.kestra.core.models.flows.sla;

import java.util.Map;

public record Violation(String slaId, SLA.Behavior behavior, Map<String, Object> labels, String reason) {
}

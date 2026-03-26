package io.kestra.core.models.flows.sla;

import java.util.List;

import io.kestra.core.models.Label;

public record Violation(String slaId, SLA.Behavior behavior, List<Label> labels, String reason) {
}

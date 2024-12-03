package io.kestra.core.models.flows.sla;

import io.kestra.core.models.Label;

import java.util.List;

public record Violation(String slaId, SLA.Behavior behavior, List<Label> labels, String reason) {
}

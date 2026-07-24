package io.kestra.webserver.models.flows;

import java.util.List;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.webserver.controllers.domain.IdWithNamespace;

public record SourceSearchReplaceApplyResponse(List<FlowWithSource> updated, List<IdWithNamespace> skipped) {
}

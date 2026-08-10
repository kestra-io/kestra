package io.kestra.webserver.models.flows;

import java.util.List;

import io.kestra.core.models.SourceMatch;

public record SourceSearchResult(String namespace, String id, boolean editable, List<SourceMatch> matches) {
}

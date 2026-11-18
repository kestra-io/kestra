package io.kestra.core.repositories;

import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;

public record SourceCodeSearchOutcome(ArrayListTotal<SearchResult<Flow>> results, boolean candidatesCapped) {
}

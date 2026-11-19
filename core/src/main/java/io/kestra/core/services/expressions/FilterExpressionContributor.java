package io.kestra.core.services.expressions;

import java.util.List;

import io.kestra.core.runners.pebble.PebbleExpressionService;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FilterExpressionContributor implements ExpressionContributor {
    private final PebbleExpressionService pebbleExpressionService;

    @Inject
    public FilterExpressionContributor(PebbleExpressionService pebbleExpressionService) {
        this.pebbleExpressionService = pebbleExpressionService;
    }

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.FILTERS;
    }

    /**
     * Returns filter names without any prefix: the {@link ExpressionCategory#FILTERS} display name already notes
     * "use as | filterName" so LLMs understand pipe syntax.
     */
    @Override
    public List<String> contribute(ExpressionRequest request) {
        return pebbleExpressionService.filters();
    }
}

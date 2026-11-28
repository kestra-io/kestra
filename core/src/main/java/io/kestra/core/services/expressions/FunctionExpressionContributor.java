package io.kestra.core.services.expressions;

import java.util.List;

import io.kestra.core.runners.pebble.PebbleExpressionService;
import io.kestra.core.runners.pebble.PebbleFunction;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FunctionExpressionContributor implements ExpressionContributor {
    private final PebbleExpressionService pebbleExpressionService;

    @Inject
    public FunctionExpressionContributor(PebbleExpressionService pebbleExpressionService) {
        this.pebbleExpressionService = pebbleExpressionService;
    }

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.FUNCTIONS;
    }

    /**
     * Returns functions as human-readable call signatures, e.g. {@code now()} or {@code secret(key='MY_SECRET')}.
     */
    @Override
    public List<String> contribute(ExpressionRequest request) {
        return pebbleExpressionService.functions().stream()
            .map(PebbleFunction::toString)
            .sorted()
            .toList();
    }
}

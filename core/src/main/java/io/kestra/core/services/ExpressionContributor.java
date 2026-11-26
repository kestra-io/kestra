package io.kestra.core.services;

import java.io.IOException;
import java.util.List;

/**
 * Supplies the expressions of one {@link ExpressionCategory} to the {@link ExpressionContextService}, which
 * collects every contributor bean; a server only gets the categories whose contributors it has on its classpath.
 */
public interface ExpressionContributor {
    ExpressionCategory category();

    /**
     * @return the expressions without the {@code {{ }}} delimiters, sorted; empty when the category needs a flow
     *     and the request has none.
     */
    List<String> contribute(ExpressionRequest request) throws IOException;
}

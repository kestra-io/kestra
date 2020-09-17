package org.kestra.core.services;

import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;

import java.util.List;

/**
 * Provides business logic to manipulate {@link Condition}
 */
public class ConditionService {
    public static boolean valid(List<Condition> list, Flow flow, Execution execution) {
        return list
            .stream()
            .allMatch(condition -> condition.test(flow, execution));
    }
}

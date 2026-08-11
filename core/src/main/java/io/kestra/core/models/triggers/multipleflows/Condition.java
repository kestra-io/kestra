package io.kestra.core.models.triggers.multipleflows;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.utils.Rethrow;

public interface Condition extends Rethrow.PredicateChecked<ConditionContext, InternalException> {
}

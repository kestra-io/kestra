package io.kestra.webserver.services.ai;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;

public class AiFreeTierEnabledCondition implements Condition {
    @Override
    public boolean matches(final ConditionContext context) {
        boolean enabled = context.getBeanContext()
            .findBean(AiFreeTierConfiguration.class)
            .map(AiFreeTierConfiguration::isEnabled)
            .orElse(false);

        if (!enabled) {
            context.fail("The hosted AI free tier is disabled");
        }

        return enabled;
    }
}

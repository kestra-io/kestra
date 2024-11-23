package io.kestra.core.runners.pebble.functions;

import io.kestra.core.services.LogService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Singleton
@Requires(property = "kestra.repository.type")
public class ErrorLogsFunction  implements Function {
    @Inject
    private LogService logService;

    @Override
    public List<String> getArgumentNames() {
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        Map<String, String> execution = (Map<String, String>) context.getVariable("execution");
        return logService.errorLogs(flow.get("tenantId"), execution.get("id"));
    }
}

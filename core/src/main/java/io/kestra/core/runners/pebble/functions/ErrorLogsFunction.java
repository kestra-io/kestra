package io.kestra.core.runners.pebble.functions;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.runners.pebble.PebbleUtils;
import io.kestra.core.services.LogService;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.RetryUtils;
import io.micronaut.context.annotation.Requires;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Singleton
@Requires(property = "kestra.repository.type")
public class ErrorLogsFunction  implements Function {
    @Inject
    private LogService logService;

    @Inject
    private PebbleUtils pebbleUtils;

    @Inject
    private RetryUtils retryUtils;

    @Override
    public List<String> getArgumentNames() {
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!pebbleUtils.calledOnWorker()) {
            throw new PebbleException(null, "The 'errorLogs' function can only be used in the Worker as it access logs from the database.", lineNumber, self.getName());
        }

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        Map<String, String> execution = (Map<String, String>) context.getVariable("execution");

        RetryUtils.Instance<List<LogEntry>, Throwable> retry = retryUtils.of(Exponential.builder()
            .delayFactor(2.0)
            .interval(Duration.ofMillis(100))
            .maxInterval(Duration.ofSeconds(1))
            .maxAttempt(-1)
            .maxDuration(Duration.ofSeconds(5))
            .build());

        try {
            return retry.run( logs -> ListUtils.isEmpty(logs), () -> logService.errorLogs(flow.get("tenantId"), execution.get("id")));
        } catch (RetryUtils.RetryFailed e) {
            return Collections.emptyList();
        } catch (Throwable e) {
            throw new PebbleException(e, "Unable to fetch error logs");
        }
    }
}

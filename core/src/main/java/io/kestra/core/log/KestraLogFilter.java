package io.kestra.core.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.boolex.EvaluationException;
import ch.qos.logback.core.boolex.EventEvaluatorBase;

public class KestraLogFilter extends EventEvaluatorBase<ILoggingEvent> {
    @Override
    public boolean evaluate(ILoggingEvent event) throws NullPointerException, EvaluationException {
        var message = event.getMessage();
        // as this filter is called very often, for perf,
        // we use startWith and do all checks successfully instead of using a more elegant construct like Stream...
        return message.startsWith("outOfOrder mode is active. Migration of schema") ||
            message.startsWith("Version mismatch         : Database version is older than what dialect POSTGRES supports") ||
            message.startsWith("Failed to bind as java.util.concurrent.Executors$AutoShutdownDelegatedExecutorService is unsupported.") ||
            message.startsWith("The cache 'default' is not recording statistics.");
    }
}

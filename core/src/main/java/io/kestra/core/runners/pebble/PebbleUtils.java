package io.kestra.core.runners.pebble;

import io.kestra.core.runners.Worker;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class PebbleUtils {
    @Value("${kestra.server-type:}") // default to empty as tests didn't set this property
    private String serverType;

    public boolean calledOnWorker() {
        if ("WORKER".equals(serverType)) {
            return true;
        }
        if ("STANDALONE".equals(serverType)) {
            // check that it's called inside a worker thread
            // Note: this is not ideal as we check that it starts with the name used in the Worker executor
            return Thread.currentThread().getName().startsWith(Worker.EXECUTOR_NAME + "_");
        }

        return false;
    }
}

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
            // check that it's called inside a worker thread.
            // Worker executor threads are named "worker-<group>_<n>" (e.g. "worker-default_0",
            // "worker-system_0"), so matching on the "worker-" prefix covers both WorkerAgent and
            // SystemWorker thread pools regardless of worker group.
            return Thread.currentThread().getName().startsWith(Worker.EXECUTOR_NAME + "-");
        }

        return false;
    }
}

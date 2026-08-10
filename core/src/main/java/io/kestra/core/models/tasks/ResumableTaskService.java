package io.kestra.core.models.tasks;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;

/**
 * Remembers, recalls and forgets a single opaque resume handle (for example a remote run id) for the
 * current taskrun, in the flow namespace KV store keyed by the taskrun id. The key is stable across a
 * worker restart and unique per execution, so a resubmitted attempt finds the same handle and handles
 * from different executions never collide.
 */
public final class ResumableTaskService {
    private static final String KEY_PREFIX = "resume_";

    private ResumableTaskService() {
    }

    /** The KV key holding the current taskrun's resume handle. */
    public static String key(RunContext runContext) {
        return KEY_PREFIX + runContext.taskRunInfo().taskRunId();
    }

    /**
     * The remembered resume handle, empty when nothing is stored or the entry has expired. A read
     * failure propagates so the caller can fail rather than silently re-trigger.
     */
    public static Optional<String> recall(RunContext runContext) throws IOException {
        try {
            return runContext.namespaceKv(runContext.flowInfo().namespace())
                .getValue(key(runContext))
                .map(value -> String.valueOf(value.value()));
        } catch (ResourceExpiredException e) {
            return Optional.empty();
        }
    }

    /** Remembers the resume handle so a later attempt can reattach. Best-effort. */
    public static void remember(RunContext runContext, String handle, Duration ttl) {
        try {
            runContext.namespaceKv(runContext.flowInfo().namespace())
                .put(key(runContext), new KVValueAndMetadata(new KVMetadata("task resume handle", ttl), handle));
        } catch (IOException e) {
            runContext.logger().warn("Could not persist resume handle, a worker restart may re-trigger the job", e);
        }
    }

    /** Forgets the resume handle once the job has reached a terminal state. Best-effort. */
    public static void forget(RunContext runContext) {
        try {
            runContext.namespaceKv(runContext.flowInfo().namespace()).delete(key(runContext));
        } catch (IOException e) {
            runContext.logger().debug("Could not delete resume handle", e);
        }
    }
}

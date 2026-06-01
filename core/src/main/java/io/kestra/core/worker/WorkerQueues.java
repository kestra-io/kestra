package io.kestra.core.worker;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Utility helpers for Worker Queue identifiers and human-friendly log formatting.
 *
 * <p>These helpers operate on a queue uid (and optionally the {@code using} tags the user
 * typed in their flow) to decide whether it refers to the default queue and to render
 * readable log messages, without depending on any concrete Worker Queue entity.
 */
public final class WorkerQueues {

    /**
     * Reserved Worker Queue id used as a sentinel for "the global default queue". No
     * Worker Queue row is ever persisted with this id; subscriptions and resolved
     * routings carry it directly to mean "dispatch to the default queue".
     */
    public static final String DEFAULT_ID = "default";

    /**
     * Reserved Worker Queue id for jobs marked with {@link io.kestra.core.models.tasks.SystemTask}.
     * <p>This id cannot be created by users; it is always reported as available so that
     * {@code SystemTask} jobs are dispatched to the SystemWorker hosted in the
     * webserver / standalone process.
     */
    public static final String SYSTEM_ID = "system";

    private WorkerQueues() {
    }

    /**
     * Canonicalize a tag set into a deterministic, human-readable string: lowercase
     * each tag, sort them, and join with hyphens. Used for display labels (e.g. metric
     * tag values) and for any place that needs a stable string derived from a tag set.
     *
     * @param tags the tag set; {@code null} or empty yields the empty string
     * @return e.g. {@code "docker-linux"} for tags {@code {"linux","docker"}}
     */
    public static String fromTags(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
            .filter(t -> t != null && !t.isBlank())
            .map(t -> t.toLowerCase(Locale.ROOT))
            .sorted()
            .collect(Collectors.joining("-"));
    }

    /**
     * Whether the given id refers to the default Worker Queue sentinel.
     *
     * @param id the Worker Queue id to test
     * @return {@code true} when the id is {@link #DEFAULT_ID}
     */
    public static boolean isDefault(String id) {
        return DEFAULT_ID.equals(id);
    }

    /**
     * Normalizes a Worker Queue id by mapping {@code null} or empty values to the
     * {@link #DEFAULT_ID} sentinel, so the rest of the system always sees a canonical
     * value.
     */
    public static String normalize(String workerQueueId) {
        return (workerQueueId == null || workerQueueId.isEmpty()) ? DEFAULT_ID : workerQueueId;
    }

    /**
     * Translates an API-side / subscription-side Worker Queue id into the routing key
     * used by the internal dispatch queue: {@code null} for the default queue (matching
     * the historical {@code workerJobEventQueue} convention), or the id verbatim for
     * named queues.
     */
    public static String toDispatchKey(String id) {
        return isDefault(id) ? null : id;
    }

    /**
     * Format a Worker Queue id for log display. Returns {@code (default)} for the global
     * default queue and the id otherwise.
     */
    public static String forLog(String id) {
        return isDefault(id) ? "(default)" : id;
    }

    /**
     * Format the user-facing label for a Worker Queue in log messages. Prefers the original
     * {@code tags} that the user typed in their flow (much more readable than the
     * resolved id), falls back to the id form, and renders {@code (default)} for the
     * global default queue.
     */
    public static String forLog(List<String> tags, String id) {
        if (tags != null && !tags.isEmpty()) {
            return "tags " + tags;
        }
        return forLog(id);
    }
}

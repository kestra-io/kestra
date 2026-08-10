package io.kestra.core.models.tasks;

import java.time.Duration;

import io.kestra.core.models.property.Property;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Marker interface for tasks that trigger a long-running external job and then poll it, so they can
 * reattach to the in-flight job after a worker restart instead of triggering a duplicate. The
 * reattach logic stays in the task's {@code run()}. This interface shares the opt-in toggle and the
 * persist/recall/forget plumbing of {@link ResumableTaskService}, keyed by the taskrun id.
 */
public interface ResumableTaskInterface {
    @Schema(
        title = "Reattach to a running job after a worker restart",
        description = "If true, the external job identifier is remembered after triggering so that, on a worker " +
            "restart, the task reattaches to the in-flight job instead of triggering a duplicate."
    )
    Property<Boolean> getResumeOnRestart();

    @Schema(
        title = "Resume handle time-to-live",
        description = "Optional expiry for the remembered job identifier. Defaults to no expiry. Set at least the " +
            "maximum expected run duration when used."
    )
    Property<Duration> getResumeTtl();
}

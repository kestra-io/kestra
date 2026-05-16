package io.kestra.core.models.tasks;

import com.cronutils.utils.VisibleForTesting;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.validations.Rfc1123Label;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Routing requirements for a Flow, Task, or Trigger.
 *
 * @param tags     required tags (RFC 1123 labels, max 20)
 * @param match    matching strategy; defaults to {@link WorkerSelectorMatch#ALL}
 * @param fallback strategy when no worker is available; defaults to {@link WorkerQueueFallback#FAIL}.
 */
public record WorkerSelector(
    @Size(max = 20)
    @Schema(description = "Required tags used to route to a matching Worker Queue. "
        + "Each tag is an RFC 1123 label: lowercase alphanumerics and hyphens, "
        + "must start and end with alphanumeric, max 64 chars per tag, max 20 tags.")
    List<@Rfc1123Label String> tags,

    @Schema(description = "How selector tags are matched against a Worker Queue's tag set. "
        + "ALL: queue tags must be a superset of the selector tags. "
        + "ANY: queue tags must intersect the selector tags. Defaults to ALL when null.")
    WorkerSelectorMatch match,

    @Schema(description = "Strategy when no worker is available. Defaults to FAIL when null.")
    WorkerQueueFallback fallback
) {

    /**
     * Creates a new {@link WorkerSelector} instance.
     *
     * @param tags     the list of tags.
     * @param fallback the fallback strategy.
     */
    @VisibleForTesting
    public WorkerSelector(List<String> tags, WorkerQueueFallback fallback) {
        this(tags, WorkerSelectorMatch.ALL, fallback);
    }

    @AssertTrue(message = "fallback can only be set when tags is non-empty")
    @JsonIgnore
    public boolean isFallbackOnlyWithTags() {
        return fallback == null || (tags != null && !tags.isEmpty());
    }

    @AssertTrue(message = "match can only be set when tags is non-empty")
    @JsonIgnore
    public boolean isMatchOnlyWithTags() {
        return match == null || (tags != null && !tags.isEmpty());
    }
}

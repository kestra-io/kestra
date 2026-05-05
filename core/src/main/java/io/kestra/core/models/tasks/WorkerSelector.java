package io.kestra.core.models.tasks;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.validations.Rfc1123Label;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Routing requirements for a Flow, Task, or Trigger.
 *
 * <p>{@code tags} drive tag-based routing to a matching Worker Queue. {@code fallback}
 * defines the resolver's behavior when no Worker Queue matches — see
 * {@link WorkerQueueFallback}.
 *
 * @param tags     the required tags (RFC 1123 labels: lowercase alphanumerics and
 *                 hyphens, must start and end with alphanumeric, max 64 chars per
 *                 tag, max 20 entries)
 * @param fallback the strategy when no Worker Queue matches; null is treated as {@link WorkerQueueFallback#FAIL}
 */
public record WorkerSelector(
    @Size(max = 20)
    @Schema(description = "Required tags used to route to a matching Worker Queue. "
        + "Each tag is an RFC 1123 label: lowercase alphanumerics and hyphens, "
        + "must start and end with alphanumeric, max 64 chars per tag, max 20 tags.")
    List<@Rfc1123Label String> tags,

    @Schema(description = "Behavior when no Worker Queue matches the tags. Defaults to FAIL when null.")
    WorkerQueueFallback fallback
) {
    @AssertTrue(message = "fallback can only be set when tags is non-empty")
    @JsonIgnore
    public boolean isFallbackOnlyWithTags() {
        return fallback == null || (tags != null && !tags.isEmpty());
    }
}

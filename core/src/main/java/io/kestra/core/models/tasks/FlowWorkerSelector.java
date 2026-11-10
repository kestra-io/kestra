package io.kestra.core.models.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.cronutils.utils.VisibleForTesting;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.PebbleUtil;
import io.kestra.core.validations.WorkerSelectorTagsValidation;
import io.kestra.core.validations.validator.Rfc1123LabelValidator;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

/**
 * Routing requirements for a Flow, Task, or Trigger, whose tags may be Pebble expressions.
 *
 * @param tags required tags, or an expression rendering to them
 * @param match matching strategy; defaults to {@link WorkerSelectorMatch#ALL}
 * @param fallback strategy when no worker is available; defaults to {@link WorkerQueueFallback#FAIL}.
 */
public record FlowWorkerSelector(
    @WorkerSelectorTagsValidation
    @Schema(
        description = "Required tags used to route to a matching Worker Queue. "
            + "Each tag is an RFC 1123 label: lowercase alphanumerics and hyphens, "
            + "must start and end with alphanumeric, max 63 chars per tag, max 20 tags. "
            + "The list, or any of its tags, can be a Pebble expression, rendered when the job is routed; "
            + "a tag rendering to an empty value is ignored, and when no tag is left the selector is treated as unset."
    ) Property<List<String>> tags,

    @Schema(
        description = "How selector tags are matched against a Worker Queue's tag set. "
            + "ALL: queue tags must be a superset of the selector tags. "
            + "ANY: queue tags must intersect the selector tags. Defaults to ALL when null."
    ) WorkerSelectorMatch match,

    @Schema(description = "Strategy when no worker is available. Defaults to FAIL when null.") WorkerQueueFallback fallback) {

    @VisibleForTesting
    public FlowWorkerSelector(List<String> tags, WorkerQueueFallback fallback) {
        this(tags == null ? null : Property.ofValue(tags), WorkerSelectorMatch.ALL, fallback);
    }

    /**
     * Renders the tags, dropping the ones that render to a blank value.
     *
     * @throws IllegalVariableEvaluationException when the tags cannot be rendered to at most 20 RFC 1123 labels
     */
    public WorkerSelector render(RunContext runContext) throws IllegalVariableEvaluationException {
        List<String> rendered = tags == null ? null : runContext.render(tags).asList(String.class);
        if (rendered == null) {
            return new WorkerSelector(null, match, fallback);
        }
        List<String> labels = new ArrayList<>(rendered.size());
        for (String tag : rendered) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String label = tag.strip();
            if (!Rfc1123LabelValidator.isValid(label)) {
                throw new IllegalVariableEvaluationException(
                    "The worker selector tag '%s' is not an RFC 1123 label.".formatted(label)
                );
            }
            labels.add(label);
        }
        if (labels.size() > WorkerSelector.MAX_TAGS) {
            throw new IllegalVariableEvaluationException(
                "The worker selector rendered %d tags, more than the maximum of %d.".formatted(labels.size(), WorkerSelector.MAX_TAGS)
            );
        }
        return new WorkerSelector(labels, match, fallback);
    }

    /**
     * Returns the tags when neither the list nor any of its tags is a Pebble expression,
     * so that they can be checked before the job is routed.
     */
    public Optional<List<String>> literalTags() {
        try {
            return Property.asStaticList(tags, String.class)
                .filter(list -> list.stream().noneMatch(tag -> tag != null && PebbleUtil.containsOpeningBlockDelimiter(tag)));
        } catch (IllegalVariableEvaluationException e) {
            return Optional.empty();
        }
    }

    @AssertTrue(message = "fallback can only be set when tags is non-empty")
    @JsonIgnore
    public boolean isFallbackOnlyWithTags() {
        return fallback == null || hasTags();
    }

    @AssertTrue(message = "match can only be set when tags is non-empty")
    @JsonIgnore
    public boolean isMatchOnlyWithTags() {
        return match == null || hasTags();
    }

    private boolean hasTags() {
        if (tags == null) {
            return false;
        }
        try {
            return Property.asStaticList(tags, String.class).map(list -> !list.isEmpty()).orElse(true);
        } catch (IllegalVariableEvaluationException e) {
            return true;
        }
    }
}

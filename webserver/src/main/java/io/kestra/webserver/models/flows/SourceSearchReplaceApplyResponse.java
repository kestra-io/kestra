package io.kestra.webserver.models.flows;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.utils.Enums;
import io.kestra.webserver.controllers.domain.IdWithNamespace;

import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;

public record SourceSearchReplaceApplyResponse(
    @Schema(description = "The flows that were rewritten, with their new revision.") List<FlowWithSource> updated,
    @Schema(description = "The flows that were left untouched, each with the reason it was skipped.") List<SkippedFlow> skipped
) {
    @Schema(description = "A flow that the replace operation did not modify, and why.")
    public record SkippedFlow(
        String namespace,
        String id,
        SkipReason reason,
        @Schema(description = "The underlying validation error, when the reason is INVALID_FLOW.") @Nullable String message
    ) {
        public static SkippedFlow of(IdWithNamespace flow, SkipReason reason) {
            return new SkippedFlow(flow.getNamespace(), flow.getId(), reason, null);
        }

        public static SkippedFlow of(IdWithNamespace flow, SkipReason reason, String message) {
            return new SkippedFlow(flow.getNamespace(), flow.getId(), reason, message);
        }
    }

    public enum SkipReason {
        /** The flow no longer exists, or was deleted between the search and the replace. */
        NOT_FOUND,
        /** The caller cannot edit the flow, or the flow carries the {@code system.readOnly} label. */
        READ_ONLY,
        /** The replacement left the source unchanged, so no new revision was needed. */
        NO_CHANGE,
        /** The requested match no longer exists at the given line and column. */
        NO_MATCH,
        /** The rewritten source is not a valid flow. */
        INVALID_FLOW,
        UNKNOWN;

        @JsonCreator
        public static SkipReason fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, SkipReason.class, UNKNOWN);
        }
    }
}

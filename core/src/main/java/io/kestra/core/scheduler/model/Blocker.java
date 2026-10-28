package io.kestra.core.scheduler.model;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.utils.Enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "What blocks the trigger's flow from running.")
public record Blocker(
    Reason reason,
    @Schema(title = "The ids of what blocks the flow, such as the rejecting policies when the reason is POLICY.") List<String> ids,
    @Schema(title = "The message reported to the user.") String message) {

    public static Blocker from(FlowBlockedException e) {
        return new Blocker(e.getReason(), e.getPolicyIds(), e.getMessage());
    }

    /**
     * The message is left out: a failed check embeds its cause, which can differ on every attempt.
     */
    public boolean isSameAs(Blocker other) {
        return other != null && reason == other.reason && Objects.equals(ids, other.ids);
    }

    public enum Reason {
        UNKNOWN,
        // a policy rule rejected the flow
        POLICY,
        // the policies could not be checked, so the flow is rejected rather than run ungoverned
        POLICY_CHECK_FAILED;

        @JsonCreator
        public static Reason fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Reason.class, UNKNOWN);
        }
    }
}

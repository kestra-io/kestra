package io.kestra.core.scheduler.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.utils.Enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "What blocks the trigger's flow from running.")
public record Blocker(
    Reason reason,
    @Schema(title = "The ids of what blocks the flow, such as the rejecting policies when the reason is POLICY.") List<String> ids,
    @Schema(title = "The message reported to the user.") String message) {

    static final String CHECK_FAILED_MESSAGE = "The governance policies could not be checked.";

    /**
     * A failed check keeps its cause out of the block, as the block is exposed by the API.
     */
    public static Blocker from(FlowBlockedException e) {
        String message = e.getReason() == Reason.POLICY_CHECK_FAILED ? CHECK_FAILED_MESSAGE : e.getMessage();
        return new Blocker(e.getReason(), e.getPolicyIds(), message);
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

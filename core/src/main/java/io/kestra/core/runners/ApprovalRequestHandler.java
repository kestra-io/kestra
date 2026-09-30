package io.kestra.core.runners;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.plugin.core.flow.Approval;
import io.kestra.plugin.core.flow.Pause;

import io.micronaut.context.annotation.Secondary;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Edition seam for the lifecycle of an {@link Approval} request. Implementations run on the executor and must be idempotent per task run, since an executor restart can replay any call.
 */
public interface ApprovalRequestHandler {

    /** Called once an Approval task run that waits for a reviewer is created; a non-null {@code url} replaces the default execution link. */
    OpenedRequest open(FlowInterface flow, Execution execution, TaskRun taskRun, Approval approval, RunContext runContext) throws Exception;

    void decided(Execution execution, TaskRun taskRun, Approval approval, Approval.Decision decision, @Nullable Pause.Resumed resumed);

    void closed(Execution execution, TaskRun taskRun, Approval approval, Resolution resolution);

    /** Throws an {@link IllegalArgumentException} when the Approval uses a property the edition does not support. */
    void validate(Approval approval);

    record OpenedRequest(@Nullable String url, @Nullable String caseId) {}

    enum Resolution {
        KILLED,
        CANCELLED
    }

    @Singleton
    @Secondary
    class NoopApprovalRequestHandler implements ApprovalRequestHandler {
        @Override
        public OpenedRequest open(FlowInterface flow, Execution execution, TaskRun taskRun, Approval approval, RunContext runContext) {
            return new OpenedRequest(null, null);
        }

        @Override
        public void decided(Execution execution, TaskRun taskRun, Approval approval, Approval.Decision decision, @Nullable Pause.Resumed resumed) {
        }

        @Override
        public void closed(Execution execution, TaskRun taskRun, Approval approval, Resolution resolution) {
        }

        @Override
        public void validate(Approval approval) {
            List<String> unsupported = new ArrayList<>();
            if (approval.getTitle() != null) {
                unsupported.add("title");
            }
            if (approval.getAssignment() != null) {
                unsupported.add("assignment");
            }
            if (approval.getCase() != null) {
                unsupported.add("case");
            }
            if (approval.getInAppNotification() != null) {
                unsupported.add("inAppNotification");
            }
            if (!unsupported.isEmpty()) {
                throw new IllegalArgumentException("Approval properties %s are only available in the Enterprise Edition.".formatted(unsupported));
            }
        }
    }
}

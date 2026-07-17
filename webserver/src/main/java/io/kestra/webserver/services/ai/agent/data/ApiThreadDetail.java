package io.kestra.webserver.services.ai.agent.data;

import java.util.List;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentThreadStatus;

import io.micronaut.core.annotation.Nullable;

public record ApiThreadDetail(
    String uid,
    @Nullable String title,
    AgentMode mode,
    AgentThreadStatus status,
    List<ApiMessageView> messages) {
    public static ApiThreadDetail from(final AgentThread thread, final List<AgentMessage> messages) {
        return new ApiThreadDetail(
            thread.uid(), thread.title(), thread.mode(), thread.status(),
            messages.stream().map(ApiMessageView::from).toList()
        );
    }
}

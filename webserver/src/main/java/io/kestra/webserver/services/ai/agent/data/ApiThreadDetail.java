package io.kestra.webserver.services.ai.agent.data;

import java.util.List;

import io.kestra.webserver.services.ai.agent.domain.AgentMessage;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentScopeBinding;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;

import io.micronaut.core.annotation.Nullable;

public record ApiThreadDetail(
    String uid,
    @Nullable String title,
    AgentMode mode,
    @Nullable AgentScopeBinding scope,
    AgentThreadStatus status,
    List<ApiMessageView> messages) {
    public static ApiThreadDetail from(final AgentThread thread, final List<AgentMessage> messages) {
        return new ApiThreadDetail(
            thread.uid(), thread.title(), thread.mode(), thread.scope(), thread.status(),
            messages.stream().map(ApiMessageView::from).toList()
        );
    }
}

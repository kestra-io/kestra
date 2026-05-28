package io.kestra.core.utils;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;

import io.kestra.core.contexts.configuration.KestraConfiguration;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class UriProvider {
    @Inject
    KestraConfiguration kestraConfiguration;

    protected URI build(String url) {
        String uri = kestraConfiguration.url();
        if (uri == null || uri.isEmpty()) {
            return null;
        }

        return URI.create(StringUtils.stripEnd(uri, "/") + url);
    }

    public URI rootUrl() {
        return this.build("/");
    }

    public URI executionUrl(Execution execution) {
        return executionUrl(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId());
    }

    public URI executionUrl(String tenantId, String namespace, String flowId, String id) {
        return this.build(
            "/ui/" +
                (tenantId != null ? tenantId + "/" : "") +
                "executions/" +
                namespace + "/" +
                flowId + "/" +
                id
        );
    }

    public URI flowUrl(Execution execution) {
        return this.build(
            "/ui/" +
                (execution.getTenantId() != null ? execution.getTenantId() + "/" : "") +
                "flows/" +
                execution.getNamespace() + "/" +
                execution.getFlowId()
        );
    }

    public URI flowUrl(FlowInterface flow) {
        return this.build(
            "/ui/" +
                (flow.getTenantId() != null ? flow.getTenantId() + "/" : "") +
                "flows/" +
                flow.getNamespace() + "/" +
                flow.getId()
        );
    }

    public URI webhookUrl(FlowInterface flow, AbstractWebhookTrigger trigger) {
        return this.build(
            "/api/v1/" +
                (flow.getTenantId() != null ? flow.getTenantId() + "/" : "") +
                "executions/webhook/" +
                flow.getNamespace() + "/" +
                flow.getId() + "/" +
                trigger.getKey()
        );
    }
}

package io.kestra.core.utils;

import io.micronaut.context.annotation.Value;
import org.apache.commons.lang3.StringUtils;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

import java.net.URI;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

@Singleton
public class UriProvider {
    @Nullable
    @Value("${kestra.url:}")
    String uri;

    private URI build(String url) {
        if (uri == null) {
            return null;
        }

        return URI.create(StringUtils.stripEnd(uri, "/") + url);
    }

    public URI rootUrl() {
        return this.build("/");
    }

    public URI executionUrl(Execution execution) {
        return this.build("/ui/" +
            (execution.getTenantId() != null ? execution.getTenantId() + "/" : "") +
            "executions/" +
            execution.getNamespace() + "/" +
            execution.getFlowId() + "/" +
            execution.getId());
    }

    public URI flowUrl(Execution execution) {
        return this.build("/ui/" +
            (execution.getTenantId() != null ? execution.getTenantId() + "/" : "") +
            "flows/" +
            execution.getNamespace() + "/" +
            execution.getFlowId());
    }

    public URI flowUrl(Flow flow) {
        return this.build("/ui/" +
            (flow.getTenantId() != null ? flow.getTenantId() + "/" : "") +
            "flows/" +
            flow.getNamespace() + "/" +
            flow.getId());
    }
}

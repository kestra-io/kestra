package io.kestra.core.utils;

import io.micronaut.context.annotation.Value;
import org.apache.commons.lang3.StringUtils;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

import java.net.URI;
import javax.annotation.Nullable;
import javax.inject.Singleton;

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
        return this.build("/ui/executions/" + execution.getNamespace() + "/" + execution.getFlowId() + "/" + execution.getId());
    }

    public URI flowUrl(Execution execution) {
        return this.build("/ui/flows/" + execution.getNamespace() + "/" + execution.getFlowId());
    }

    public URI flowUrl(Flow flow) {
        return this.build("/ui/flows/" + flow.getNamespace() + "/" + flow.getId());
    }
}

package org.kestra.core.utils;

import io.micronaut.context.annotation.Value;
import org.apache.commons.lang3.StringUtils;
import org.kestra.core.models.executions.Execution;

import java.net.URI;
import javax.annotation.Nullable;
import javax.inject.Singleton;

@Singleton
public class UriProvider {
    @Nullable
    @Value("${kestra.url:}")
    String uri;

    public URI executionUrl(Execution execution) {
        if (uri == null) {
            return null;
        }

        return URI.create(
            StringUtils.stripEnd(uri, "/") +
                "/ui/executions/" + execution.getNamespace() + "/" + execution.getFlowId() + "/" + execution.getId()
        );
    }
}

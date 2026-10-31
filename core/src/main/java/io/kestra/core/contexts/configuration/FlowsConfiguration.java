
package io.kestra.core.contexts.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.flows")
public record FlowsConfiguration(
    @Bindable(defaultValue = "false") boolean hardDelete) {
}

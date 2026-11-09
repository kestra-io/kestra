package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.EditionProvider;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "kestra.url", value = "https://kestra.example.com")
@Property(name = "kestra.environment.name", value = "staging")
class ReadInstanceInfoToolTest {
    @Inject
    private ReadInstanceInfoTool tool;

    @Inject
    private VersionProvider versionProvider;

    @Inject
    private EditionProvider editionProvider;

    @Inject
    private SystemFlowsConfiguration systemFlowsConfiguration;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldDescribeTheRunningInstanceWhenInvoked() {
        ReadInstanceInfoTool.Result result = tool.readInstanceInfo(AgentCallContext.Context.ofTenant(MAIN_TENANT));

        assertThat(result.version()).isEqualTo(versionProvider.getVersion());
        assertThat(result.edition()).isEqualTo(editionProvider.get().name());
        assertThat(result.url()).isEqualTo("https://kestra.example.com");
        assertThat(result.environment()).isEqualTo("staging");
        assertThat(result.systemNamespace()).isEqualTo(systemFlowsConfiguration.namespace());
    }
}

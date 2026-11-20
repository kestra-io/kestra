package io.kestra.webserver.services.ai.agent.tool;

import java.time.ZonedDateTime;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.utils.EditionProvider;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.Tool;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ReadInstanceInfoTool implements McpPlatformTool {
    private final VersionProvider versionProvider;
    private final EditionProvider editionProvider;
    private final SystemFlowsConfiguration systemFlowsConfiguration;
    private final String url;
    private final String environment;

    @Inject
    public ReadInstanceInfoTool(
        final VersionProvider versionProvider,
        final EditionProvider editionProvider,
        final SystemFlowsConfiguration systemFlowsConfiguration,
        @Nullable @Value("${kestra.url}") final String url,
        @Nullable @Value("${kestra.environment.name}") final String environment) {
        this.versionProvider = versionProvider;
        this.editionProvider = editionProvider;
        this.systemFlowsConfiguration = systemFlowsConfiguration;
        this.url = url;
        this.environment = environment;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "read_instance_info",
        value = "Describe the Kestra instance you are connected to: its version, edition and public URL. Read-only; use this to know which features exist, or to build links to flows and executions. "
            + "Returns an object { version, commitId, commitDate, edition, url, environment, systemNamespace }: `edition` is OSS or EE, `environment` is the name the operator gave this instance (e.g. production), "
            + "and `url` and `environment` are null when not configured."
    )
    public Result readInstanceInfo(final AgentCallContext.Context context) {
        ZonedDateTime date = versionProvider.getDate();
        return new Result(
            versionProvider.getVersion(),
            versionProvider.getRevision(),
            date == null ? null : date.toString(),
            editionProvider.get().name(),
            url,
            environment,
            systemFlowsConfiguration.namespace()
        );
    }

    public record Result(String version, String commitId, String commitDate, String edition, String url, String environment, String systemNamespace) {
    }
}

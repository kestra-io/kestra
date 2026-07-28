package io.kestra.core.junit.extensions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.kestra.core.junit.services.TestTenantLifecycle;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.MicronautJunit5Extension;

import static io.kestra.core.junit.extensions.ExtensionUtils.loadFile;
import static io.kestra.core.utils.Rethrow.throwFunction;
import static org.awaitility.Awaitility.await;

public abstract class AbstractLoaderExtension {

    protected ApplicationContext context;

    protected void loadApplicationContext(ExtensionContext extensionContext) {
        if (context == null) {
            // Try KestraTestExtension namespace (used by @KestraTest)
            context = extensionContext.getRoot().getStore(
                ExtensionContext.Namespace.create(KestraTestExtension.class, extensionContext.getTestClass().get())
            ).get(ApplicationContext.class, ApplicationContext.class);

            // Fallback to MicronautJunit5Extension namespace (used by @MicronautTest)
            if (context == null) {
                context = extensionContext.getRoot().getStore(
                    ExtensionContext.Namespace.create(MicronautJunit5Extension.class)
                ).get(ApplicationContext.class, ApplicationContext.class);
            }

            if (context == null) {
                throw new IllegalStateException(
                    "No application context, to use this annotation you need to add '@KestraTest' or '@MicronautTest'"
                );
            }
        }
    }

    /**
     * Ensure the tenant a fixture targets exists before loading resources into it. No-op in OSS;
     * Enterprise replaces {@link TestTenantLifecycle} to actually create the tenant.
     */
    protected void createTenant(ExtensionContext extensionContext, String tenantId) {
        loadApplicationContext(extensionContext);
        context.getBean(TestTenantLifecycle.class).create(tenantId);
    }

    /**
     * Delete a tenant previously created via {@link #createTenant(ExtensionContext, String)}.
     * No-op in OSS; best-effort in Enterprise (only tenants it created are removed).
     */
    protected void deleteTenant(String tenantId) {
        if (context == null || !context.isRunning()) {
            return;
        }
        context.getBean(TestTenantLifecycle.class).delete(tenantId);
    }

    protected void loadFlows(ExtensionContext extensionContext, String tenantId, String[] paths)
        throws IOException, URISyntaxException {
        loadApplicationContext(extensionContext);

        LocalFlowRepositoryLoader repositoryLoader = context.getBean(
            LocalFlowRepositoryLoader.class
        );

        // A path may be a single flow file or a directory: the loader walks it recursively and
        // returns every flow it actually created/updated (with its assigned revision), so the
        // metastore-wait below operates on real flows regardless of whether a directory
        // (e.g. "flows/valids") was passed.
        List<FlowWithSource> loadedFlows = new ArrayList<>();
        for (String path : paths) {
            URL resource = loadFile(path);

            loadedFlows.addAll(TestsUtils.loads(tenantId, repositoryLoader, resource));
        }

        // The flow metastore's cache is updated asynchronously (via a queue subscription) after
        // FlowService.create()/update() returns, so a Flow trigger on a freshly-loaded flow can
        // miss its first executions. Await until every loaded flow is visible in the cache, so tests
        // never race their own fixtures. Best-effort: a few valid flows never reach the cache (their
        // queue event fails to deserialize, e.g. task aliases) — they are still persisted, so we
        // don't fail the fixture on them.
        FlowMetaStoreInterface flowMetaStore = context.getBean(FlowMetaStoreInterface.class);
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                Collection<FlowWithSource> cached = flowMetaStore.allLastVersion();
                return loadedFlows.stream().allMatch(flow -> cached.stream().anyMatch(
                    c -> tenantId.equals(c.getTenantId())
                        && c.getNamespace().equals(flow.getNamespace())
                        && c.getId().equals(flow.getId())
                        && c.getRevision().equals(flow.getRevision())
                ));
            });
        } catch (ConditionTimeoutException ignored) {
            // Best-effort — some loaded flows never reach the cache (see above); they are persisted.
        }
    }

    protected void deleteFlows(String tenantId, String[] paths) throws URISyntaxException, IOException {
        if (!context.isRunning()) {
            return;
        }

        FlowRepositoryInterface flowRepository = context.getBean(FlowRepositoryInterface.class);
        ExecutionRepositoryInterface executionRepository = context.getBean(ExecutionRepositoryInterface.class);

        // A path may be a single flow file or a directory: resolve it to every flow it declares so
        // directory fixtures (e.g. "flows/valids") are cleaned up as thoroughly as they were loaded.
        Set<String> flowIds = new HashSet<>();
        for (String path : paths) {
            getFlows(tenantId, path).forEach(flow -> flowIds.add(flow.getId()));
        }
        flowRepository.findAllForAllTenants().stream()
            .filter(flow -> flowIds.contains(flow.getId()))
            .filter(flow -> tenantId.equals(flow.getTenantId()))
            .forEach(flow ->
            {
                flowRepository.deleteWithoutAcl(flow);
                executionRepository.findByFlowId(tenantId, flow.getNamespace(), flow.getId(), Pageable.UNPAGED)
                    .forEach(executionRepository::delete);
            });
    }

    protected static Flow getFlow(String path) throws URISyntaxException {
        URL resource = loadFile(path);
        Flow flow = YamlParser.parse(Paths.get(resource.toURI()).toFile(), Flow.class);
        return flow;
    }

    /**
     * Resolves a resource path to the flows it declares. The path may be a single flow file or a
     * directory, in which case it is walked recursively for {@code *.yaml}/{@code *.yml} files.
     */
    protected static List<GenericFlow> getFlows(String tenantId, String path) throws URISyntaxException, IOException {
        Path resource = Paths.get(loadFile(path).toURI());

        try (Stream<Path> pathStream = Files.walk(resource)) {
            return pathStream
                .filter(YamlParser::isValidExtension)
                .map(throwFunction(file -> GenericFlow.fromYaml(tenantId, Files.readString(file, Charset.defaultCharset()))))
                .toList();
        }
    }
}

package io.kestra.core.junit.extensions;
import static io.kestra.core.junit.extensions.ExtensionUtils.loadFile;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
public class FlowLoaderExtension implements BeforeEachCallback, AfterEachCallback, AfterAllCallback {
    private ApplicationContext applicationContext;
    private static final Object lock =new Object();
    private final Set<Flow> allFlows= new HashSet<>();
    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (applicationContext == null) {
            extensionContext.getRoot().getStore(ExtensionContext.Namespace.create(KestraTestExtension.class, extensionContext.getTestClass().get())).put("test", "bla");
            applicationContext = extensionContext.getRoot().getStore(ExtensionContext.Namespace.create(KestraTestExtension.class, extensionContext.getTestClass().get()))
                .get(ApplicationContext.class, ApplicationContext.class);
            if (applicationContext == null) {
                throw new IllegalStateException(
                    "No application context, to use '@LoadFlows' annotation, you need to add '@KestraTest'");
            }
        }
        LocalFlowRepositoryLoader repositoryLoader = applicationContext.getBean(
            LocalFlowRepositoryLoader.class);
        LoadFlows loadFlows = getLoadFlows(extensionContext);
        for (String path : loadFlows.value()) {
            URL resource = loadFile(path);
            TestsUtils.loads(loadFlows.tenantId(), repositoryLoader, resource);
        }
    }
    @Override
    public void afterEach(ExtensionContext extensionContext) throws URISyntaxException {
        LoadFlows loadFlows = getLoadFlows(extensionContext);
        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
        Set<String> flowIds = new HashSet<>();
        for (String path : loadFlows.value()) {
            URL resource = loadFile(path);
            Flow flow = YamlParser.parse(Paths.get(resource.toURI()).toFile(), Flow.class);
            flowIds.add(flow.getId());
        }
        List<Flow> flows = flowRepository.findAllForAllTenants().stream()
            .filter(flow -> flowIds.contains(flow.getId()))
            .filter(flow -> loadFlows.tenantId().equals(flow.getTenantId()))
            .toList();
        allFlows.addAll(flows);
    }
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
        ExecutionRepositoryInterface executionRepository = applicationContext.getBean(ExecutionRepositoryInterface.class);
        synchronized (lock){
            allFlows.forEach(flow -> {
                Optional<Flow> fresh = flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId());
                fresh.ifPresent(freshFlow -> {
                        flowRepository.delete(FlowWithSource.of(freshFlow, "unused"));
                        executionRepository.findByFlowId(
                            freshFlow.getTenantId(), freshFlow.getNamespace(),freshFlow.getId(), Pageable.UNPAGED
                        ).forEach(executionRepository::delete);
                    }
                );
            });
            allFlows.clear();
        }
    }

    private static LoadFlows getLoadFlows(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(LoadFlows.class);
    }

}
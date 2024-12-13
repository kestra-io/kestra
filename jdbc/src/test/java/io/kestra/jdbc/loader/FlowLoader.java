package io.kestra.jdbc.loader;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.utils.Rethrow.RunnableChecked;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;

@Singleton
public class FlowLoader {

    @Inject
    private FlowRepositoryInterface flowRepository;
    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    public void executeWithFlow(List<String> flowPaths, RunnableChecked runnableChecked)
        throws Exception {

        for (String path : flowPaths) {
            TestsUtils.loads(repositoryLoader, Objects.requireNonNull(TestsUtils.class.getClassLoader().getResource(path)));
        }

        try {
            runnableChecked.run();
        } catch (Exception e){
            throw e;
        } finally {
            flowRepository.findAllForAllTenants().forEach(flow -> flowRepository.delete(
                FlowWithSource.of(flow, "unused")));
        }
    }
}

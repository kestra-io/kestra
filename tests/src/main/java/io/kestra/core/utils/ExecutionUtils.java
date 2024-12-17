package io.kestra.core.utils;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URL;
import java.util.List;
import java.util.Objects;

@Singleton
public class ExecutionUtils {
    @Inject
    LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    FlowRepositoryInterface flowRepository;

    @SuppressWarnings("CaughtExceptionImmediatelyRethrown")
    public <E extends Exception> void loadFlows(List<String> flowPaths, Rethrow.RunnableChecked<E> runnableChecked) throws Exception {
        for (String path : flowPaths) {
            URL resource = TestsUtils.class.getClassLoader().getResource(path);
            if (resource == null) {
                throw new IllegalArgumentException("Unable to load flow: " + path);
            }

            TestsUtils.loads(repositoryLoader, resource);
        }

        try {
            runnableChecked.run();
        } catch (Exception e){
            throw e;
        } finally {
            flowRepository
                .findAllForAllTenants()
                .forEach(flow -> flowRepository.delete(FlowWithSource.of(flow, "unused")));
        }
    }
}

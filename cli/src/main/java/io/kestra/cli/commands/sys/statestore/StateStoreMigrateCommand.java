package io.kestra.cli.commands.sys.statestore;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StateStore;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Slugify;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@CommandLine.Command(
    name = "migrate",
    description = "Migrate old state store files to use the new KV Store implementation.",
    mixinStandardHelpOptions = true
)
@Slf4j
public class StateStoreMigrateCommand extends AbstractCommand {
    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private RunContextFactory runContextFactory;

    @Override
    public Integer call() throws Exception {
        flowRepository.findAllForAllTenants().stream().map(flow -> Map.entry(flow, List.of(
            URI.create("/" + flow.getNamespace().replace(".", "/") + "/" + Slugify.of(flow.getId()) + "/states"),
            URI.create("/" + flow.getNamespace().replace(".", "/") + "/states")
        ))).map(potentialStateStoreUrisForAFlow -> Map.entry(potentialStateStoreUrisForAFlow.getKey(), potentialStateStoreUrisForAFlow.getValue().stream().flatMap(uri -> {
            try {
                return storageInterface.allByPrefix(potentialStateStoreUrisForAFlow.getKey().getTenantId(), uri, false).stream();
            } catch (IOException e) {
                return Stream.empty();
            }
        }).toList())).forEach(stateStoreFileUrisForAFlow -> stateStoreFileUrisForAFlow.getValue().forEach(stateStoreFileUri -> {
            Flow flow = stateStoreFileUrisForAFlow.getKey();
            String[] flowQualifierWithStateQualifiers = stateStoreFileUri.getPath().split("/states/");
            String[] statesUriPart = flowQualifierWithStateQualifiers[1].split("/");

            String stateName = statesUriPart[0];
            String taskRunValue = statesUriPart.length > 2 ? statesUriPart[1] : null;
            String stateSubName = statesUriPart[statesUriPart.length - 1];
            boolean flowScoped = flowQualifierWithStateQualifiers[0].endsWith("/" + flow.getId());
            StateStore stateStore = new StateStore(runContext(flow), false);

            try (InputStream is = storageInterface.get(flow.getTenantId(), stateStoreFileUri)) {
                stateStore.putState(flowScoped, stateName, stateSubName, taskRunValue, is.readAllBytes());
                storageInterface.delete(flow.getTenantId(), stateStoreFileUri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));

        stdOut("Successfully ran the state-store migration.");
        return 0;
    }

    private RunContext runContext(Flow flow) {
        return runContextFactory.of(flow, Map.of("flow", Map.of(
            "tenantId", flow.getTenantId(),
            "id", flow.getId(),
            "namespace", flow.getNamespace()
        )));
    }
}

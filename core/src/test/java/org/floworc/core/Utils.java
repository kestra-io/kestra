package org.floworc.core;

import com.devskiller.friendly_id.FriendlyId;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.repositories.LocalFlowRepositoryLoader;
import org.floworc.core.runners.RunContext;
import org.floworc.core.serializers.YamlFlowParser;
import org.floworc.core.storages.StorageInterface;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;

abstract public class Utils {
    private static final YamlFlowParser yamlFlowParser = new YamlFlowParser();

    public static Flow parse(String path) throws IOException {
        URL resource = Utils.class.getClassLoader().getResource(path);
        assertNotNull(resource);

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file);
    }

    public static void loads(LocalFlowRepositoryLoader repositoryLoader) throws IOException, URISyntaxException {
        Utils.loads(repositoryLoader, "flows/valids");
    }

    public static void loads(LocalFlowRepositoryLoader repositoryLoader, String path) throws IOException, URISyntaxException {
        URL url = Objects.requireNonNull(Utils.class.getClassLoader().getResource(path));
        repositoryLoader.load(url);
    }

    public static RunContext mockRunContext(StorageInterface storageInterface, Task task, Map<String, Object> inputs){
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];

        Flow flow = Flow.builder()
            .namespace(caller.getClassName())
            .id(caller.getMethodName())
            .revision(1)
            .build();

        Execution execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .inputs(inputs)
            .state(new State())
            .build()
            .withState(State.Type.RUNNING);

        TaskRun taskRun = TaskRun.builder()
            .id(FriendlyId.createFriendlyId())
            .executionId(execution.getId())
            .flowId(flow.getId())
            .taskId(task.getId())
            .state(new State())
            .build()
            .withState(State.Type.RUNNING);

        return new RunContext(flow, task, execution, taskRun)
            .withStorageInterface(storageInterface);
    }
}

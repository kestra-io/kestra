package io.kestra.webserver.services.playground;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.utils.ListUtils;

/**
 * Keys task runs by where they sit in a run (parent path, task id, value, iteration), which a replay keeps while it renews their ids.
 */
final class TaskRunPositions {
    private TaskRunPositions() {
    }

    static Map<String, TaskRun> byPosition(Execution execution) {
        List<TaskRun> taskRuns = ListUtils.emptyOnNull(execution.getTaskRunList());
        Map<String, TaskRun> byId = taskRuns.stream().collect(Collectors.toMap(TaskRun::getId, Function.identity(), (a, b) -> a));
        Map<String, TaskRun> byPosition = new LinkedHashMap<>();
        taskRuns.forEach(taskRun -> byPosition.putIfAbsent(position(taskRun, byId, taskRuns.size()), taskRun));
        return byPosition;
    }

    private static String position(TaskRun taskRun, Map<String, TaskRun> byId, int depthLimit) {
        String own = "%s[%s#%s]".formatted(taskRun.getTaskId(), Objects.toString(taskRun.getValue(), ""), Objects.toString(taskRun.getIteration(), ""));
        TaskRun parent = taskRun.getParentTaskRunId() == null ? null : byId.get(taskRun.getParentTaskRunId());
        return parent == null || depthLimit <= 0 ? own : position(parent, byId, depthLimit - 1) + "/" + own;
    }
}

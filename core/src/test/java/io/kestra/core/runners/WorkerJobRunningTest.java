package io.kestra.core.runners;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stored shape of a running entry changed in 2.0: both subtypes moved their payload into a
 * {@code data} field. Entries written by a 1.x worker survive an upgrade in {@code worker_job_running}
 * and, because the type ignores unknown properties, they deserialize silently with {@code data} left
 * {@code null}. These tests pin that down so such an entry is recognized rather than acted on.
 */
@KestraTest
class WorkerJobRunningTest {

    private static final String LEGACY_TRIGGER_JSON = """
        {
          "type": "trigger",
          "partition": 0,
          "workerInstance": {"uid": "60172c81-93fb-4ad4-b835-4792787b6e92", "workerGroup": null},
          "trigger": {"id": "watch_http", "type": "io.kestra.plugin.core.http.Trigger", "uri": "http://localhost:28080/"},
          "triggerContext": {
            "tenantId": "main",
            "namespace": "company.tmc",
            "flowId": "legacy_repro",
            "triggerId": "watch_http",
            "date": "2026-09-01T00:00:00Z"
          },
          "conditionContext": {}
        }
        """;

    private static final String LEGACY_TASK_JSON = """
        {
          "type": "task",
          "partition": 0,
          "workerInstance": {"uid": "60172c81-93fb-4ad4-b835-4792787b6e92", "workerGroup": null},
          "task": {"id": "log", "type": "io.kestra.plugin.core.log.Log", "message": "test"},
          "taskRun": {
            "id": "5cBZ1JhBnZkPqGmJvVqYtm",
            "executionId": "7HhPuWEpReMMKmWYsljudc",
            "namespace": "company.tmc",
            "flowId": "legacy_repro",
            "taskId": "log",
            "state": {"current": "RUNNING", "histories": []}
          },
          "runContext": {"variables": {}}
        }
        """;

    @Test
    void shouldFlagAsLegacyWhenTriggerEntryWasWrittenByPreviousVersion() throws Exception {
        WorkerJobRunning running = JacksonMapper.ofJson().readValue(LEGACY_TRIGGER_JSON, WorkerJobRunning.class);

        // The 1.x fields are dropped as unknown and the read still succeeds — which is exactly why the
        // entry reaches code that dereferences `data`.
        assertThat(running).isInstanceOf(WorkerTriggerRunning.class);
        assertThat(((WorkerTriggerRunning) running).getData()).isNull();
        assertThat(running.isLegacy()).isTrue();
    }

    @Test
    void shouldFlagAsLegacyWhenTaskEntryWasWrittenByPreviousVersion() throws Exception {
        WorkerJobRunning running = JacksonMapper.ofJson().readValue(LEGACY_TASK_JSON, WorkerJobRunning.class);

        assertThat(running).isInstanceOf(WorkerTaskRunning.class);
        assertThat(((WorkerTaskRunning) running).getData()).isNull();
        assertThat(running.isLegacy()).isTrue();
    }

    @Test
    void shouldNotFlagAsLegacyWhenEntryIsCurrent() throws Exception {
        WorkerTaskRunning current = WorkerTaskRunning.builder()
            .workerInstance(new WorkerInstance(IdUtils.create(), null))
            .taskRun(
                TaskRun.builder()
                    .id(IdUtils.create())
                    .executionId(IdUtils.create())
                    .namespace("io.kestra.unittest")
                    .flowId("worker-job-running")
                    .taskId("log")
                    .state(new State().withState(State.Type.RUNNING))
                    .build()
            )
            .task(Log.builder().id("log").type(Log.class.getName()).message("test").build())
            .data(new WorkerTaskData(Map.of(), null))
            .build();

        String json = JacksonMapper.ofJson().writeValueAsString(current);
        WorkerJobRunning running = JacksonMapper.ofJson().readValue(json, WorkerJobRunning.class);

        assertThat(running.isLegacy()).isFalse();
    }
}

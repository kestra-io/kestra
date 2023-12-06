package io.kestra.core.runners;

import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@Singleton
public class DeserializationIssuesCaseTest {
    private static final String INVALID_WORKER_TASK_KEY = "5PGRX6ve2cztrRSIbfGphO";
    private static final String INVALID_WORKER_TASK_VALUE = """
        {
          "task": {
            "id": "invalid",
            "type": "io.kestra.notfound.Invalid"
          },
          "type": "task",
          "taskRun": {
            "id": "5PGRX6ve2cztrRSIbfGphO",
            "state": {
              "current": "CREATED",
              "duration": 0.058459656,
              "histories": [
                {
                  "date": "2023-11-28T10:16:22.324536603Z",
                  "state": "CREATED"
                }
              ],
              "startDate": "2023-11-28T10:16:22.324536603Z"
            },
            "flowId": "hello-world",
            "taskId": "hello",
            "namespace": "company.team",
            "executionId": "7IBX10Tg3ZzZuNUnLhoXcT"
          },
          "runContext": {
            "variables": {
              "envs": {
                "plugins_path": "/home/loic/dev/kestra-plugins"
              },
              "flow": {
                "id": "hello-world",
                "revision": 1,
                "namespace": "company.team"
              },
              "task": {
                "id": "hello",
                "type": "io.kestra.core.tasks.log.Log"
              },
              "taskrun": {
                "id": "5PGRX6ve2cztrRSIbfGphO",
                "startDate": "2023-11-28T10:16:22.324536603Z",
                "attemptsCount": 0
              },
              "execution": {
                "id": "7IBX10Tg3ZzZuNUnLhoXcT",
                "startDate": "2023-11-28T10:16:21.648Z",
                "originalId": "7IBX10Tg3ZzZuNUnLhoXcT"
              }
            },
            "storageOutputPrefix": "///company/team/hello-world/executions/7IBX10Tg3ZzZuNUnLhoXcT/tasks/hello/5PGRX6ve2cztrRSIbfGphO"
          }
        }""";

    private static final String INVALID_WORKER_TRIGGER_KEY = "dev_http-trigger_http";
    private static final String INVALID_WORKER_TRIGGER_VALUE = """
        {
          "type": "trigger",
          "trigger": {
            "id": "invalid",
            "type": "io.kestra.notfound.Invalid"
          },
          "triggerContext": {
            "date": "2023-11-24T15:48:57.632881597Z",
            "flowId": "http-trigger",
            "namespace": "dev",
            "triggerId": "http",
            "flowRevision": 3
          },
          "conditionContext": {
            "flow": {
              "id": "http-trigger",
              "tasks": [
                {
                  "id": "hello",
                  "type": "io.kestra.core.tasks.log.Log",
                  "message": "Kestra team wishes you a great day! 👋"
                }
              ],
              "deleted": false,
              "disabled": false,
              "revision": 3,
              "triggers": [
                {
                  "id": "invalid",
                  "type": "io.kestra.notfound.Invalid"
                }
              ],
              "namespace": "dev"
            },
            "runContext": {
              "variables": {
                "envs": {
                  "plugins_path": "/home/loic/dev/kestra-plugins"
                },
                "flow": {
                  "id": "http-trigger",
                  "revision": 3,
                  "namespace": "dev"
                },
                "trigger": {
                  "id": "invalid",
                  "type": "io.kestra.notfound.Invalid"
                }
              }
            }
          }
        }
        """;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    protected QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    protected QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;

    public record QueueMessage(Class<?> type, String key, String value) {}


    public void workerTaskDeserializationIssue(Consumer<QueueMessage> sendToQueue) throws TimeoutException {
        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>(null);
        workerTaskResultQueue.receive(either -> workerTaskResult.set(either.getLeft()));

        sendToQueue.accept(new QueueMessage(WorkerJob.class, INVALID_WORKER_TASK_KEY, INVALID_WORKER_TASK_VALUE));

        Await.until(
            () -> workerTaskResult.get() != null && workerTaskResult.get().getTaskRun().getState().isTerminated(),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
        assertThat(workerTaskResult.get().getTaskRun().getState().getHistories().size(), is(2));
        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent(), is(State.Type.FAILED));
    }

    public void workerTriggerDeserializationIssue(Consumer<QueueMessage> sendToQueue) throws InterruptedException {
        AtomicReference<WorkerTriggerResult> workerTriggerResult = new AtomicReference<>(null);
        workerTriggerResultQueue.receive(either -> workerTriggerResult.set(either.getLeft()));

        sendToQueue.accept(new QueueMessage(WorkerJob.class, INVALID_WORKER_TRIGGER_KEY, INVALID_WORKER_TRIGGER_VALUE));

        // Invalid worker trigger will be ignored, so we just check that no messages are received
        Thread.sleep(500);
        assertThat(workerTriggerResult.get(), nullValue());
    }

}

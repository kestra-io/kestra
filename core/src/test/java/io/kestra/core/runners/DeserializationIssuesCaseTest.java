package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
                "type": "io.kestra.plugin.core.log.Log"
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
                  "type": "io.kestra.plugin.core.log.Log",
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

    private static final String INVALID_FLOW_KEY = "company.team_hello-world_2";
    private static final String INVALID_FLOW_VALUE = """
        {
          "id": "hello-world",
          "tasks": [
            {
              "id": "invalid",
              "type": "io.kestra.notfound.Invalid"
            }
          ],
          "deleted": false,
          "disabled": false,
          "revision": 2,
          "namespace": "company.team"
        }
        """;

    public static final String INVALID_SUBFLOW_EXECUTION_KEY = "1XKpihp8y2m3KEHR0hVEKN";
    public static final String INVALID_SUBFLOW_EXECUTION_VALUE =  """
    {
      "execution": {
        "id": "1XKpihp8y2m3KEHR0hVEKN",
        "state": {
          "current": "CREATED",
          "duration": 0.000201173,
          "histories": [
            {
              "date": "2024-01-10T13:48:32.752Z",
              "state": "CREATED"
            }
          ],
          "startDate": "2024-01-10T13:48:32.752Z"
        },
        "flowId": "hello-world",
        "deleted": false,
        "trigger": {
          "id": "subflow",
          "type": "io.kestra.notfound.Invalid",
          "variables": {
            "flowId": "subflox",
            "namespace": "company.team",
            "executionId": "4NzSyOQBYj1CxVg3bTghbZ",
            "flowRevision": 1
          }
        },
        "namespace": "company.team",
        "originalId": "1XKpihp8y2m3KEHR0hVEKN",
        "flowRevision": 2
      },
      "parentTask": {
        "id": "subflow",
        "type": "io.kestra.notfound.Invalid"
      },
      "parentTaskRun": {
        "id": "6Gc6Dkk7medsWtg1WJfZpN",
        "state": {
          "current": "RUNNING",
          "duration": 0.039446974,
          "histories": [
            {
              "date": "2024-01-10T13:48:32.713Z",
              "state": "CREATED"
            },
            {
              "date": "2024-01-10T13:48:32.752Z",
              "state": "RUNNING"
            }
          ],
          "startDate": "2024-01-10T13:48:32.713Z"
        },
        "flowId": "subflox",
        "taskId": "subflow",
        "namespace": "company.team",
        "executionId": "4NzSyOQBYj1CxVg3bTghbZ"
      }
    }
    """;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    protected QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    protected QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;

    @Inject
    private FlowListenersInterface flowListeners;

    public record QueueMessage(Class<?> type, String key, String value) {}


    public void workerTaskDeserializationIssue(Consumer<QueueMessage> sendToQueue) throws TimeoutException {
        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>();
        Flux<WorkerTaskResult> receive = TestsUtils.receive(workerTaskResultQueue, either -> {
            if (either != null) {
                workerTaskResult.set(either.getLeft());
            }
        });

        sendToQueue.accept(new QueueMessage(WorkerJob.class, INVALID_WORKER_TASK_KEY, INVALID_WORKER_TASK_VALUE));

        Await.until(
            () -> workerTaskResult.get() != null && workerTaskResult.get().getTaskRun().getState().isTerminated(),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
        receive.blockLast();
        assertThat(workerTaskResult.get().getTaskRun().getState().getHistories().size(), is(2));
        assertThat(workerTaskResult.get().getTaskRun().getState().getHistories().get(0).getState(), is(State.Type.CREATED));
        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent(), is(State.Type.FAILED));
    }

    public void workerTriggerDeserializationIssue(Consumer<QueueMessage> sendToQueue) throws TimeoutException {
        AtomicReference<WorkerTriggerResult> workerTriggerResult = new AtomicReference<>();
        Flux<WorkerTriggerResult> receive = TestsUtils.receive(workerTriggerResultQueue, either -> {
            if (either != null) {
                workerTriggerResult.set(either.getLeft());
            }
        });

        sendToQueue.accept(new QueueMessage(WorkerJob.class, INVALID_WORKER_TRIGGER_KEY, INVALID_WORKER_TRIGGER_VALUE));

        Await.until(
            () -> workerTriggerResult.get() != null,
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
        receive.blockLast();
        assertThat(workerTriggerResult.get().getSuccess(), is(Boolean.FALSE));
    }

    public void flowDeserializationIssue(Consumer<QueueMessage> sendToQueue) throws TimeoutException {
        AtomicReference<List<Flow>> flows = new AtomicReference<>();
        flowListeners.listen(newFlows -> flows.set(newFlows));

        sendToQueue.accept(new QueueMessage(Flow.class, INVALID_FLOW_KEY, INVALID_FLOW_VALUE));

        Await.until(
            () -> flows.get() != null && flows.get().stream().anyMatch(newFlow -> newFlow.uid().equals("company.team_hello-world_2") && (newFlow.getTasks() == null || newFlow.getTasks().isEmpty())),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
    }
}

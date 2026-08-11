package io.kestra.core.runners;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.scheduler.events.TriggerEvent;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Singleton
public class DeserializationIssuesCaseTest {
    private static final String INVALID_WORKER_TASK_KEY = "5PGRX6ve2cztrRSIbfGphO";
    private static final String INVALID_WORKER_TASK_VALUE = """
        {
            "job": {
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
            }
        }""";

    private static final String INVALID_WORKER_TRIGGER_KEY = "dev_http-trigger_http";
    private static final String INVALID_WORKER_TRIGGER_VALUE = """
        {
          "job": {
              "type": "trigger",
              "trigger": {
                "id": "invalid",
                "type": "io.kestra.notfound.Invalid"
              },
              "triggerContext": {
                "date": "2023-11-24T15:48:57.632881597Z",
                "flowId": "http-trigger",
                "namespace": "dev",
                "triggerId": "http"
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

    @Inject
    protected DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    private VNodeDispatchQueueInterface<TriggerEvent> triggerEventQueue;

    @Inject
    protected BroadcastQueueInterface<FlowInterface> flowQueue;

    public record QueueMessage(Class<?> type, String key, String value) {
    }

    public void workerTaskDeserializationIssue(Consumer<QueueMessage> sendToQueue) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        workerTaskResultQueue.addListener(workerTaskResult ->
        {
            if (workerTaskResult.getTaskRun().getId().equals(INVALID_WORKER_TASK_KEY)) {
                countDownLatch.countDown();
            }
        });

        sendToQueue.accept(new QueueMessage(WorkerJobEvent.class, INVALID_WORKER_TASK_KEY, INVALID_WORKER_TASK_VALUE));
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    public void workerTriggerDeserializationIssue(Consumer<QueueMessage> sendToQueue) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        triggerEventQueue.addListener(triggerEvent ->
        {
            if (triggerEvent.id().uid().equals(INVALID_WORKER_TRIGGER_KEY)) {
                countDownLatch.countDown();
            }
        });

        sendToQueue.accept(new QueueMessage(WorkerJobEvent.class, INVALID_WORKER_TRIGGER_KEY, INVALID_WORKER_TRIGGER_VALUE));
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    public void flowDeserializationIssue(Consumer<QueueMessage> sendToQueue) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        var subscriber = flowQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft()) {
                FlowInterface flowInterface = either.getLeft();
                if (flowInterface.uid().equals("company.team_hello-world_2")) {
                    countDownLatch.countDown();
                }
            }
        });

        try {
            sendToQueue.accept(new QueueMessage(FlowInterface.class, INVALID_FLOW_KEY, INVALID_FLOW_VALUE));
            assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        } finally {
            subscriber.close();
        }
    }
}

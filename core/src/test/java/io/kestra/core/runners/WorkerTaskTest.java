package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

class WorkerTaskTest {
    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void deserializeOldWorkerTask() throws JsonProcessingException {
        var workerTask = MAPPER.readValue("""
            {
              "task": {
                "id": "log",
                "type": "io.kestra.plugin.core.log.Log",
                "message": "{{taskrun.value}}"
              },
              "taskRun": {
                "id": "40KIWqYv0YKVLhoBxqBWSc",
                "state": {
                  "current": "CREATED",
                  "duration": 0.097935348,
                  "histories": [
                    {
                      "date": "2023-06-20T11:57:54.381Z",
                      "state": "CREATED"
                    }
                  ],
                  "startDate": "2023-06-20T11:57:54.381Z"
                },
                "value": "{\\"date\\":\\"2023-06-19T17:00:00Z\\",\\"title\\":\\"La_Rivière_rouge\\",\\"views\\":141}",
                "flowId": "for-each",
                "taskId": "log",
                "namespace": "io.kestra.tests",
                "executionId": "3dZ8ymxFCkClQBb06m42yF",
                "parentTaskRunId": "4fEQD16l9Hbnmfd3OVRdBX"
              },
              "runContext": {
                "variables": {
                  "envs": {
                    "plugins_path": "/home/loic/dev/kestra-plugins"
                  },
                  "flow": {
                    "id": "for-each",
                    "revision": 3,
                    "namespace": "io.kestra.tests"
                  },
                  "task": {
                    "id": "log",
                    "type": "io.kestra.plugin.core.log.Log"
                  },
                  "outputs": {
                    "query-top-ten": {
                      "rows": [
                        {
                          "date": "2023-06-19T17:00:00Z",
                          "title": "Adeline_Chetail",
                          "views": 637
                        },
                        {
                          "date": "2023-06-19T17:00:00Z",
                          "title": "Crispín_d'Olot",
                          "views": 571
                        }
                      ],
                      "size": 1000,
                      "jobId": "job_dhbRbhsRGIFnUHmjPIhLqbDwCGco",
                      "destinationTable": {
                        "table": "anonev_IOjsokKWbdTszZYJOd_o2Dkt5NjXa5UqJiHrvqru8vY",
                        "dataset": "_52cd107af79c85adaf7d5cfbd999c8fe976c55f9",
                        "project": "methodical-mesh-238712"
                      }
                    }
                  },
                  "taskrun": {
                    "id": "40KIWqYv0YKVLhoBxqBWSc",
                    "value": "{\\"date\\":\\"2023-06-19T17:00:00Z\\",\\"title\\":\\"La_Rivière_rouge\\",\\"views\\":141}",
                    "parentId": "4fEQD16l9Hbnmfd3OVRdBX",
                    "startDate": "2023-06-20T11:57:54.381Z",
                    "attemptsCount": 0
                  },
                  "execution": {
                    "id": "3dZ8ymxFCkClQBb06m42yF",
                    "startDate": "2023-06-20T11:57:42.092Z",
                    "originalId": "3dZ8ymxFCkClQBb06m42yF"
                  }
                },
                "storageOutputPrefix": "/io/kestra/tests/for-each/executions/3dZ8ymxFCkClQBb06m42yF/tasks/log/40KIWqYv0YKVLhoBxqBWSc"
              }
            }""", WorkerJob.class);

        assertThat(workerTask, notNullValue());
        assertThat(workerTask, instanceOf(WorkerTask.class));
    }

}
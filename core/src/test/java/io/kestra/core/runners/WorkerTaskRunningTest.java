package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

class WorkerTaskRunningTest {
    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void deserializeOldWorkerTask() throws JsonProcessingException {
        var workerTaskRunning = MAPPER.readValue("""
            {
              "taskRun": {
                "id": "3RpDLNAPLhaiqkH5JSuIYw",
                "executionId": "1cBacTDmTNHmqFTGkDc5qe",
                "namespace": "io.kestra.tests",
                "flowId": "trigger-polling-6",
                "taskId": "log",
                "state": {
                  "current": "CREATED",
                  "histories": [
                    {
                      "state": "CREATED",
                      "date": "2023-06-22T09:50:27.719269008Z"
                    }
                  ],
                  "duration": 0.134760877,
                  "startDate": "2023-06-22T09:50:27.719269008Z"
                }
              },
              "task": {
                "id": "log",
                "type": "io.kestra.plugin.core.log.Log",
                "message": "Row: {{trigger.row}}"
              },
              "runContext": {
                "storageOutputPrefix": "/io/kestra/tests/trigger-polling-6/executions/1cBacTDmTNHmqFTGkDc5qe/tasks/log/3RpDLNAPLhaiqkH5JSuIYw",
                "variables": {
                  "envs": {
                    "plugins_path": "/home/loic/dev/kestra-plugins"
                  },
                  "task": {
                    "id": "log",
                    "type": "io.kestra.plugin.core.log.Log"
                  },
                  "taskrun": {
                    "id": "3RpDLNAPLhaiqkH5JSuIYw",
                    "startDate": "2023-06-22T09:50:27.719269008Z",
                    "attemptsCount": 0
                  },
                  "flow": {
                    "id": "trigger-polling-6",
                    "namespace": "io.kestra.tests",
                    "revision": 1
                  },
                  "execution": {
                    "id": "1cBacTDmTNHmqFTGkDc5qe",
                    "startDate": "2023-06-22T09:50:27.708528339Z",
                    "originalId": "1cBacTDmTNHmqFTGkDc5qe"
                  },
                  "trigger": {
                    "row": {
                      "current_timestamp": "2023-06-22T11:50:27.706904+02:00"
                    },
                    "size": 1
                  }
                }
              },
              "workerInstance": {
                "workerUuid": "99146a76-1f21-49ad-bef4-92af0bd1df3c",
                "hostname": "loic-16Z90Q-G-AD78F",
                "partitions": [
                  14,
                  15,
                  12,
                  13,
                  10,
                  11,
                  0,
                  1,
                  8,
                  9,
                  6,
                  7,
                  4,
                  5,
                  2,
                  3
                ]
              },
              "partition": 0
            }
            """, WorkerJobRunning.class);

        assertThat(workerTaskRunning, notNullValue());
        assertThat(workerTaskRunning, instanceOf(WorkerTaskRunning.class));
    }

}
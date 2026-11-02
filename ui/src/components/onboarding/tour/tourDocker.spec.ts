import {describe, expect, it} from "vitest"

import {isDockerSocketFailure} from "./tourDocker"
import {TOUR_SUMMARIZE_TASK_ID} from "./tourFlows"

const DOCKER_JAVA_MESSAGE = "Docker execution failed because Docker socket is not accessible.\n\n"
    + "Fix:\n"
    + "- Mount docker socket:\n"
    + "  -v /var/run/docker.sock:/var/run/docker.sock\n"
    + "- OR use Process runner:\n"
    + "  taskRunner:\n"
    + "    type: io.kestra.plugin.core.runner.Process\n\n"
    + "Tried Docker host: unix:///var/run/docker.sock"

describe("docker socket failure detection", () => {
    it.each([
        ["the message the Docker task runner logs", "ERROR", TOUR_SUMMARIZE_TASK_ID, DOCKER_JAVA_MESSAGE, true],
        ["the message in another case", "ERROR", TOUR_SUMMARIZE_TASK_ID, DOCKER_JAVA_MESSAGE.toUpperCase(), true],
        ["another error on the task", "ERROR", TOUR_SUMMARIZE_TASK_ID, "ModuleNotFoundError: No module named 'kestra'", false],
        ["the message on another task", "ERROR", "notify", DOCKER_JAVA_MESSAGE, false],
        ["the message below the error level", "INFO", TOUR_SUMMARIZE_TASK_ID, DOCKER_JAVA_MESSAGE, false],
    ] as const)("on %s", (_name, level, taskId, message, expected) => {
        expect(isDockerSocketFailure([{level, taskId, message}])).toBe(expected)
    })
})

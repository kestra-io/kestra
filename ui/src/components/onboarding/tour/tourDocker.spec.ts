import {describe, expect, it} from "vitest"

import {isDockerSocketFailure} from "./tourDocker"

const DOCKER_JAVA_MESSAGE = "Docker execution failed because Docker socket is not accessible.\n\n"
    + "Fix:\n"
    + "- Mount docker socket:\n"
    + "  -v /var/run/docker.sock:/var/run/docker.sock\n"
    + "- OR use Process runner:\n"
    + "  taskRunner:\n"
    + "    type: io.kestra.plugin.core.runner.Process\n\n"
    + "Tried Docker host: unix:///var/run/docker.sock"

describe("docker socket failure detection", () => {
    it("matches the error the Docker task runner logs", () => {
        expect(isDockerSocketFailure([
            {level: "ERROR", taskId: "summarize", message: DOCKER_JAVA_MESSAGE},
        ])).toBe(true)
    })

    it("matches regardless of case", () => {
        expect(isDockerSocketFailure([
            {level: "ERROR", taskId: "summarize", message: DOCKER_JAVA_MESSAGE.toUpperCase()},
        ])).toBe(true)
    })

    it("ignores other errors on the task", () => {
        expect(isDockerSocketFailure([
            {level: "ERROR", taskId: "summarize", message: "ModuleNotFoundError: No module named 'kestra'"},
        ])).toBe(false)
    })

    it("ignores the marker on another task", () => {
        expect(isDockerSocketFailure([
            {level: "ERROR", taskId: "notify", message: DOCKER_JAVA_MESSAGE},
        ])).toBe(false)
    })

    it("ignores the marker below the error level", () => {
        expect(isDockerSocketFailure([
            {level: "INFO", taskId: "summarize", message: DOCKER_JAVA_MESSAGE},
        ])).toBe(false)
    })

    it("is false without logs", () => {
        expect(isDockerSocketFailure([])).toBe(false)
    })
})

import type {LogEntry} from "@kestra-io/kestra-sdk"
import {TOUR_SUMMARIZE_TASK_ID} from "./tourFlows"

export const DOCKER_SOCKET_MARKER = "Docker execution failed because Docker socket is not accessible."

export const isDockerSocketFailure = (logs: readonly Pick<LogEntry, "level" | "taskId" | "message">[]) =>
    logs.some((log) =>
        log.level === "ERROR"
        && log.taskId === TOUR_SUMMARIZE_TASK_ID
        && (log.message ?? "").toLowerCase().includes(DOCKER_SOCKET_MARKER.toLowerCase()),
    )

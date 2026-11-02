import type {LogEntry} from "@kestra-io/kestra-sdk"

export const DOCKER_SOCKET_MARKER = "Docker execution failed because Docker socket is not accessible."

export const DOCKER_TASK_ID = "summarize"

export const isDockerSocketFailure = (logs: readonly Pick<LogEntry, "level" | "taskId" | "message">[]) =>
    logs.some((log) =>
        log.level === "ERROR"
        && log.taskId === DOCKER_TASK_ID
        && (log.message ?? "").toLowerCase().includes(DOCKER_SOCKET_MARKER.toLowerCase()),
    )

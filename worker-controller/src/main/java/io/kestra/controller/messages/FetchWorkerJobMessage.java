package io.kestra.controller.messages;

public record FetchWorkerJobMessage(
    String workerId,
    String workerGroup
) {
}

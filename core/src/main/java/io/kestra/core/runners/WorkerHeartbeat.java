package io.kestra.core.runners;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@SuperBuilder(toBuilder = true)
@ToString
@NoArgsConstructor
public class WorkerHeartbeat extends WorkerInstance {
    @Builder.Default
    private Status status = Status.UP;

    @Builder.Default
    private Instant heartbeatDate = Instant.now();

    public WorkerInstance toWorkerInstance() {
        return WorkerInstance.builder()
            .workerUuid(this.getWorkerUuid())
            .hostname(this.getHostname())
            .port(this.getPort())
            .managementPort(this.getManagementPort())
            .workerGroup(this.getWorkerGroup())
            .build();
    }

    public enum Status {
        UP, DEAD
    }
}

package io.kestra.core.runners;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
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

    public enum Status {
        UP, DEAD
    }
}

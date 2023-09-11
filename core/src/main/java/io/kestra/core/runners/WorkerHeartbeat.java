package io.kestra.core.runners;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.sql.Timestamp;
import java.time.Instant;

@Data
@SuperBuilder(toBuilder = true)
@ToString
@NoArgsConstructor
public class WorkerHeartbeat extends WorkerInstance {
    @Builder.Default
    private Status status = Status.UP;

    @Builder.Default
    private Timestamp heartbeatDate = Timestamp.from(Instant.now());

    public enum Status {
        UP, DEAD
    }
}

package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder(toBuilder = true)
@ToString
@NoArgsConstructor
@Getter
public class WorkerInstance {
    @NotNull
    private UUID workerUuid;

    @NotNull
    private String hostname;
    private Integer port;
    private Integer managementPort;

    private String workerGroup;

    @Builder.Default
    private List<Integer> partitions = new ArrayList<>();

    @Builder.Default
    @JsonInclude
    private Status status = Status.UP;

    @Builder.Default
    private Instant heartbeatDate = Instant.now();

    public enum Status {
        UP, DEAD
    }

}

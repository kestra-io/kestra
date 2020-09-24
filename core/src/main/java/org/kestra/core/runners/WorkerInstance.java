package org.kestra.core.runners;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.validation.constraints.NotNull;

@Data
@Builder
@ToString
public class WorkerInstance {
    @NotNull
    private UUID workerUuid;

    @NotNull
    private String hostname;

    @Builder.Default
    @NotNull
    private List<Integer> partitions = new ArrayList<>();
}

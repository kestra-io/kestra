package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@ToString
@NoArgsConstructor
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkerInstance {
    @NotNull
    private String workerUuid;

    private String workerGroup;

    @Builder.Default
    private List<Integer> partitions = new ArrayList<>();

}

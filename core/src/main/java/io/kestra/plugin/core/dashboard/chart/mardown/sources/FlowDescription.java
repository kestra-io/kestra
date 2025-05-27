package io.kestra.plugin.core.dashboard.chart.mardown.sources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Schema(
    title = "Flow Source to fetch description"
)
@Getter
public class FlowDescription extends MarkdownSource {
    @Schema(
        title = "Flow ID"
    )
    @NotNull
    private String flowId;

    @Schema(
        title = "Flow Namespace"
    )
    @NotNull
    private String namespace;
}

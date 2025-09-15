package io.kestra.plugin.core.dashboard.chart.mardown.sources;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(
    title = "Markdown from text"
)
public class Text extends MarkdownSource {
    @Schema(
        title = "Markdown content to display"
    )
    private String content;
}

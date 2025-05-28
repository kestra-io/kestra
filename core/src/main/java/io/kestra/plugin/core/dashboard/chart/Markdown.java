package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ChartOption;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.plugin.core.dashboard.chart.mardown.sources.MarkdownSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@Schema(
    title = "Add context and insights with customizable Markdown text."
    )
@Plugin(
    examples = {
        @Example(
            title = "Display custom content in place with Markdown.",
            full = true,
            code = {
                "charts:\n" +
                    "- id: markdown_insight\n" +
                    "type: io.kestra.plugin.core.dashboard.chart.Markdown\n" +
                    "chartOptions:\n" +
                        "displayName: Chart Insights\n" +
                        "description: How to interpret this chart\n" +
                    "content: \"## Execution Success Rate\n" +
                               "This chart displays the percentage of successful executions over time.\n" +
                               
                               "- A **higher success rate** indicates stable and reliable workflows.\n" +

                               "- Sudden **drops** may signal issues in task execution or external dependencies.\n" +

                               "- Use this insight to identify trends and optimize performance.\"\n"
            }
        )
    }
)
public class Markdown extends Chart<ChartOption> {
    @Deprecated(forRemoval = true)
    @Schema(
        title = "[DEPRECATED]Markdown content to display",
        description = "Use the String source instead"
    )
    private String content;

    private MarkdownSource source;
}

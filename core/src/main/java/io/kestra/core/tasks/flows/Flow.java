package io.kestra.core.tasks.flows;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a subflow execution. Subflows offer a modular way to reuse workflow logic by calling other flows just like calling a function in a programming language.",
    description = "This task is deprecated, please use the `io.kestra.core.tasks.flows.Subflow` task instead."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a subflow with custom inputs.",
            code = {
                "namespace: dev",
                "flowId: subflow",
                "id:",
                "  user: \"Rick Astley\"",
                "  favorite_song: \"Never Gonna Give You Up\"",
                "wait: true",
                "transmitFailed: true"
            }
        )
    }
)
@Deprecated
public class Flow extends Subflow {}

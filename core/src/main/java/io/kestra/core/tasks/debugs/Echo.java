package io.kestra.core.tasks.debugs;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.Task;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Log a message in the task logs.",
    description = "This task is deprecated, please use the `io.kestra.core.tasks.log.Log` task instead.",
    deprecated = true
)
@Plugin(
    examples = {
        @Example(
            code = {
                "level: WARN",
                "format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
@Deprecated
public class Echo extends Task {}

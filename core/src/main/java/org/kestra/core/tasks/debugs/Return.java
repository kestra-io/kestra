package org.kestra.core.tasks.debugs;

import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.slf4j.Logger;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Return extends Task implements RunnableTask {
    private String format;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(this.getClass());

        String render = runContext.render(format);
        logger.debug(render);

        return RunOutput.builder()
            .outputs(ImmutableMap.of("return", render))
            .build();
    }
}

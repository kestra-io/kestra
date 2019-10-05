package org.floworc.core.tasks.debugs;

import com.google.common.collect.ImmutableMap;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.slf4j.Logger;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Value
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

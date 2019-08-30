package org.floworc.core.tasks.flows;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.tasks.FlowableTask;
import org.floworc.core.tasks.Task;

import java.util.List;
import java.util.Map;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Switch extends Task implements FlowableTask {
    private Map<String, List<Task>> cases;

    private List<Task> defaults;
}

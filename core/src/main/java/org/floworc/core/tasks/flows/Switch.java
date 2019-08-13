package org.floworc.core.tasks.flows;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.tasks.Task;

import java.util.List;
import java.util.Map;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Switch extends Task {
    private Map<String, List<Task>> cases;

    private List<Task> defaults;

    @Override
    public Void run() {
        log.info("Starting '{}'", this);

        return null;
    }
}

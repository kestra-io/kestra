package org.floworc.core.tasks.debugs;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.tasks.Task;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Echo extends Task {
    private String format;

    @Override
    public Void run() throws Exception {
        log.info(this.format);
        return null;
    }
}

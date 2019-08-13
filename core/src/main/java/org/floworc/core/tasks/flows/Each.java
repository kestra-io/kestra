package org.floworc.core.tasks.flows;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Each extends Parallel {
    private List<String> values;
}

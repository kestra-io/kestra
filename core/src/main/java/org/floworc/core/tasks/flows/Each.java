package org.floworc.core.tasks.flows;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Value
@Slf4j
public class Each extends Parallel {
    private List<String> values;
}

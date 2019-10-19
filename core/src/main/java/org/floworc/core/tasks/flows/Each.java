package org.floworc.core.tasks.flows;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Getter
@FieldDefaults(level= AccessLevel.PROTECTED)
@AllArgsConstructor
public class Each extends Parallel {
    private List<String> values;
}

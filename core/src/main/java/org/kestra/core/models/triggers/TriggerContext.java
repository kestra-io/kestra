package org.kestra.core.models.triggers;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.Arrays;
import javax.validation.constraints.NotNull;

@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
public class TriggerContext {
    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    @NotNull
    private Integer flowRevision;

    @NotNull
    private String triggerId;

    @NotNull
    private ZonedDateTime date;

    public String uid() {
        return uid(this);
    }

    public static String uid(TriggerContext trigger) {
        return String.join("_", Arrays.asList(
            trigger.getNamespace(),
            trigger.getFlowId(),
            trigger.getTriggerId()
        ));
    }
}

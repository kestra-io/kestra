package io.kestra.core.models.triggers;

import io.kestra.core.models.property.Property;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@SuperBuilder
@Getter
public abstract class AbstractStatefulTrigger extends AbstractTrigger implements StatefulTriggerInterface {
    @Builder.Default
    protected final Property<On> on = Property.ofValue(On.CREATE_OR_UPDATE);
    protected final Property<String> stateKey;
    protected final Property<Duration> stateTtl;
}
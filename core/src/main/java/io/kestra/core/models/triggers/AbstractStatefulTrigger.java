package io.kestra.core.models.triggers;


import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

/**
 * Base class for triggers that watch for new or updated items.
 * Tracks state in the KV Store to avoid duplicate runs.
 *
 * <p>Supports {@code on}, {@code stateKey}, and {@code stateTtl} properties
 * to control when the trigger fires and how its state is stored.
 */
@SuperBuilder
@Getter
public abstract class AbstractStatefulTrigger extends AbstractTrigger implements StatefulTriggerInterface {

    @Schema(
        title = "Trigger event type",
        description = """
            Defines when the trigger fires.
            - `CREATE`: only for newly discovered entities.
            - `UPDATE`: only when an already-seen entity changes.
            - `CREATE_OR_UPDATE`: fires on either event.
            """
    )
    @Builder.Default
    protected final Property<On> on = Property.ofValue(On.CREATE_OR_UPDATE);

    @Schema(
        title = "State key",
        description = """
            JSON-type KV key for persisted state.
            Default: `<namespace>__<flowId>__<triggerId>`
            """
    )
    protected final Property<String> stateKey;

    @Schema(
        title = "State TTL",
        description = "TTL for persisted state entries (e.g., PT24H, P7D)."
    )
    protected final Property<Duration> stateTtl;
}

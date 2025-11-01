package io.kestra.core.models.triggers;

import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;

public interface StatefulTriggerInterface {
    @Schema(
        title = "Trigger event type",
        description = """
            Defines when the trigger fires.
            - `CREATE`: only for newly discovered entities.
            - `UPDATE`: only when an already-seen entity changes.
            - `CREATE_OR_UPDATE`: fires on either event.
            """
    )
    Property<On> getOn();

    @Schema(
        title = "State key",
        description = """
            JSON-type KV key for persisted state.
            Default: `<namespace>__<flowId>__<triggerId>`
            """
    )
    Property<String> getStateKey();

    @Schema(
        title = "State TTL",
        description = "TTL for persisted state entries (e.g., PT24H, P7D)."
    )
    Property<Duration> getStateTtl();

    enum On {
        CREATE,
        UPDATE,
        CREATE_OR_UPDATE
    }
}

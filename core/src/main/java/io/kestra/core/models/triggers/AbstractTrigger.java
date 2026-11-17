package io.kestra.core.models.triggers;

import java.util.List;
import java.util.Map;

import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.assets.TriggerAssetsDeclaration;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.FlowWorkerSelector;
import io.kestra.core.serializers.Jackson3ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.Jackson3ListOrMapOfLabelSerializer;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.validations.NoSystemLabelValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Plugin
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract public class AbstractTrigger implements TriggerInterface {
    @Size(max = 256, message = "Trigger id must be at most 256 characters")
    protected String id;

    protected String type;

    @PluginProperty(hidden = true, group = "advanced")
    protected String version;

    @PluginProperty(hidden = true, group = "advanced")
    private String description;

    @Builder.Default
    @NotNull
    @PluginProperty(group = "execution", dynamic = true)
    @Schema(
        title = "A condition that determines whether the trigger should run.",
        description = "A Pebble expression evaluated at trigger time. The trigger fires only when the expression evaluates to a truthy value (`true`, a non-empty string, a non-zero number). Use this to gate trigger execution on dynamic runtime values such as execution labels, flow variables, or environment conditions."
    )
    private String when = "true";

    @Builder.Default
    @PluginProperty(hidden = true, group = "execution")
    @Schema(defaultValue = "false")
    private boolean disabled = false;

    @Valid
    @PluginProperty(hidden = true, group = "execution")
    @Schema(description = "Routing requirements (tags + fallback) for this trigger.")
    private FlowWorkerSelector workerSelector;

    @PluginProperty(hidden = true, group = "advanced")
    @Schema(
        description = "Identifiers of `enforcement: REFERENCE` governance policies to attach to this trigger and everything nested under it (Enterprise Edition; ignored in the open-source edition)."
    )
    private List<String> policyRefs;

    @PluginProperty(hidden = true, group = "logging")
    private Level logLevel;

    @Schema(
        title = "The labels to pass to the execution created.",
        description = "Label values are dynamic and can reference trigger variables.",
        implementation = Object.class, oneOf = { List.class, Map.class }
    )
    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    @tools.jackson.databind.annotation.JsonSerialize(using = Jackson3ListOrMapOfLabelSerializer.class)
    @tools.jackson.databind.annotation.JsonDeserialize(using = Jackson3ListOrMapOfLabelDeserializer.class)
    @PluginProperty(hidden = true, group = "advanced", dynamic = true)
    private List<@NoSystemLabelValidation Label> labels;

    @PluginProperty(group = "reliability")
    @Schema(
        title = "List of execution states after which a trigger should be stopped (a.k.a. disabled)."
    )
    private List<State.Type> stopAfter;

    @Builder.Default
    @PluginProperty(hidden = true, group = "logging")
    private boolean logToFile = false;

    @Builder.Default
    @PluginProperty(hidden = true, group = "reliability")
    private boolean failOnTriggerError = false;

    @Builder.Default
    @PluginProperty(group = "execution")
    @Schema(
        title = "Specifies whether a trigger is allowed to start a new execution even if a previous run is still in progress."
    )
    private boolean allowConcurrent = false;

    @PluginProperty(hidden = true, group = "advanced")
    @Valid
    private TriggerAssetsDeclaration assets;

    private static final String MIGRATION_GUIDE = "https://kestra.io/docs/migration-guide/v2.0.0";

    /**
     * Rejects the pre-2.0 trigger filtering properties instead of dropping them.
     * <p>
     * The mappers used on the read paths — the flow repositories, and {@code parseForRuntime} — are lenient,
     * so an undeclared property is normally dropped and the trigger runs <em>without</em> it. For
     * {@code conditions} / {@code preconditions}, which are what kept a trigger from firing, that turns a
     * narrow trigger into one that matches everything: a 1.3 flow could start an execution storm right after
     * an upgrade. Failing here makes such a flow surface as a
     * {@link io.kestra.core.models.flows.FlowWithException} — listable and visible in the UI, but skipped by
     * the executor, the scheduler and the flow-trigger service — exactly like a trigger whose <em>type</em>
     * no longer exists.
     * <p>
     * Only the removed <em>filtering</em> properties are rejected, and by name. A property that merely
     * changed name or moved — {@code workerGroup}, {@code pluginDefaultsRef}, {@code Schedule.backfill} —
     * keeps being dropped as before: losing it does not change what the trigger matches, and bricking the
     * flow over it would be worse than the drop.
     */
    @JsonSetter("conditions")
    void rejectRemovedConditions(Object ignored) {
        throw removed("conditions", "\"when\" (and by \"dependsOn\" on io.kestra.plugin.core.trigger.Flow)");
    }

    /** @see #rejectRemovedConditions(Object) */
    @JsonSetter("preconditions")
    void rejectRemovedPreconditions(Object ignored) {
        throw removed("preconditions", "\"dependsOn\"");
    }

    /**
     * An {@link IllegalArgumentException} is deliberate: Jackson wraps it into a {@code JsonMappingException},
     * which every read path already handles — the repositories turn it into a {@code FlowWithException},
     * {@code YamlParser} into a {@code ConstraintViolationException}. A runtime exception of another kind
     * would escape those handlers.
     * <p>
     * The trigger is named only when its {@code id} was read before the offending property, which is the
     * order of both the YAML users write and the stored flow JSON; otherwise the message says
     * {@code "<unknown>"} and the caller identifies the trigger from the flow and the property.
     */
    protected IllegalArgumentException removed(String property, String replacement) {
        return new IllegalArgumentException(
            "Trigger \"" + (this.id == null ? "<unknown>" : this.id) + "\" ("
                + (this.type == null ? this.getClass().getName() : this.type) + ") uses \"" + property
                + "\", which was removed in 2.0 and replaced by " + replacement
                + " - see the migration guide " + MIGRATION_GUIDE
        );
    }
}

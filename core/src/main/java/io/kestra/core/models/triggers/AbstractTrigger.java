package io.kestra.core.models.triggers;

import java.util.List;
import java.util.Map;

import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.kestra.core.exceptions.UnknownPropertyException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.WorkerSelector;
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
    private WorkerSelector workerSelector;

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
    private AssetsDeclaration assets;

    private static final String MIGRATION_GUIDE = "https://kestra.io/docs/migration-guide/v2.0.0";

    /**
     * Properties that existed on a trigger before 2.0 and were removed by it. Deserializing them silently
     * would drop the filtering the user configured — the removed `conditions` / `preconditions` are what kept
     * a trigger from firing — so they get a message pointing at their replacement rather than the generic one.
     */
    private static final Map<String, String> REMOVED_PROPERTIES = Map.of(
        "conditions",
        "trigger conditions were replaced by \"when\" in 2.0 (and by \"dependsOn\" on io.kestra.plugin.core.trigger.Flow) - see the migration guide " + MIGRATION_GUIDE,
        "preconditions",
        "trigger preconditions were replaced by \"dependsOn\" in 2.0 - see the migration guide " + MIGRATION_GUIDE
    );

    /**
     * Fails deserialization on any property that is not part of the trigger model.
     * <p>
     * Triggers must fail closed: the mappers used on the read paths (the flow repositories, and
     * {@code parseForRuntime}) are lenient, so without this an unknown property is dropped and the trigger
     * runs <em>without</em> it. For a filtering property such as the pre-2.0 {@code conditions} /
     * {@code preconditions} that turns a narrow trigger into one that matches everything, which is how a
     * 1.3 flow could start an execution storm right after an upgrade. Throwing here makes such a flow surface
     * as a {@link io.kestra.core.models.flows.FlowWithException} — listable and visible in the UI, but skipped
     * by the executor, the scheduler and the flow-trigger service — exactly like a trigger whose <em>type</em>
     * no longer exists.
     * <p>
     * An {@link UnknownPropertyException} — an {@link IllegalArgumentException} — is deliberate: Jackson wraps
     * it into a {@code JsonMappingException}, which every read path already handles (the repositories turn it
     * into a {@code FlowWithException}, {@code YamlParser} into a {@code ConstraintViolationException}). A raw
     * runtime exception of another kind would escape those handlers.
     * <p>
     * The trigger is named only when its {@code id} was read before the offending property, which is the order
     * of both the YAML users write and the stored flow JSON; otherwise the message says {@code "<unknown>"} and
     * the caller identifies the trigger from the flow and the property.
     * <p>
     * Consequences to know about before touching a trigger class:
     * <ul>
     * <li>Jackson consults an any-setter <em>before</em> its unknown-property handling, so neither
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)} on a trigger nor a mapper's
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} has any effect on triggers any more. Ignoring a specific property
     * still works: {@code @JsonIgnoreProperties({"foo"})} names it explicitly, and Jackson drops it before
     * reaching here.</li>
     * <li>A trigger deserialized through a builder — {@code @Jacksonized}, or any
     * {@code @JsonDeserialize(builder = …)} — does not go through this method, which silently re-opens the
     * hole for that trigger. Do not add one without moving the check into the builder.</li>
     * <li>A flow authored against a <em>newer</em> plugin version than the one installed (a rollback, or a
     * pinned version) fails the same way and becomes a {@code FlowWithException}: a property the installed
     * class does not declare is indistinguishable from a removed one, and failing closed is the safe reading
     * of both.</li>
     * </ul>
     */
    @JsonAnySetter
    public void failOnUnknownProperty(String name, Object value) {
        throw new UnknownPropertyException(
            "Unrecognized property \"" + name + "\" on trigger \"" + (this.id == null ? "<unknown>" : this.id) + "\" ("
                + (this.type == null ? this.getClass().getName() : this.type) + "): "
                + REMOVED_PROPERTIES.getOrDefault(name, "this property does not exist on this trigger")
        );
    }
}

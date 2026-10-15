package io.kestra.core.models.flows.check;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a check within a Kestra flow.
 * <p>
 * A {@code Check} defines a boolean condition that is evaluated when validating flow's inputs
 * and before triggering an execution.
 * <p>
 * If the condition evaluates to {@code false}, the configured {@link Behavior}
 * determines how the execution proceeds, and the {@link Style} determines how
 * the message is visually presented in the UI.
 * </p>
 */
@SuperBuilder
@Getter
@NoArgsConstructor
public class Check {

    /**
     * The condition to evaluate.
     * <p>
     * The check passes when the expression renders to {@code true}; when it renders to
     * {@code false}, the configured {@code message}, {@code style} and {@code behavior} apply.
     * The expression must render to a real boolean {@code true} or {@code false}. A value that renders
     * to anything else (for example the string {@code "true"} or {@code "yes"}, or a number) is treated
     * as a <em>failed</em> check, because evaluation uses a strict {@code Boolean.TRUE.equals(...)} test.
     * <p>
     * If the expression cannot be evaluated at all (for example an undefined variable or a syntax error),
     * the check fails safe: the execution is hard-blocked with {@link Behavior#BLOCK_EXECUTION} and
     * {@link Style#ERROR}, overriding the declared {@code behavior} and {@code style} — a broken gate must
     * never let a run through.
     */
    @NotNull
    @NotEmpty
    String passIf;

    /**
     * @deprecated renamed to {@link #passIf}; still accepted when reading a flow, never written
     * back. Write-only so existing flows keep working while new and re-saved flows use
     * {@code passIf}.
     */
    @Deprecated(forRemoval = true, since = "2.1.0")
    @JsonProperty(value = "when", access = JsonProperty.Access.WRITE_ONLY)
    @Getter(AccessLevel.NONE)
    @Builder.Default
    String when = null;

    /**
     * @deprecated renamed to {@link #passIf}; still accepted when reading a flow, never written
     * back. Write-only so existing flows keep working while new and re-saved flows use
     * {@code passIf}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    @JsonProperty(value = "condition", access = JsonProperty.Access.WRITE_ONLY)
    @Getter(AccessLevel.NONE)
    @Builder.Default
    String condition = null;

    /**
     * Tracks which condition property name was used while deserializing, so a check that mixes
     * {@code passIf}, {@code when} and {@code condition} can be rejected instead of silently
     * keeping whichever came last. Never serialized.
     */
    @JsonIgnore
    @Builder.Default
    String conditionProperty = null;

    @JsonSetter("passIf")
    public void setPassIf(String passIf) {
        this.passIf = conditionValue("passIf", passIf);
    }

    /**
     * @deprecated use {@code passIf} instead.
     */
    @Deprecated(forRemoval = true, since = "2.1.0")
    @JsonSetter("when")
    public void setWhen(String when) {
        this.passIf = conditionValue("when", when);
    }

    /**
     * @deprecated use {@code passIf} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    @JsonSetter("condition")
    public void setCondition(String condition) {
        this.passIf = conditionValue("condition", condition);
    }

    /**
     * Returns the condition expression, whichever name it was declared with: {@code passIf}
     * or the deprecated {@code when} / {@code condition} aliases.
     */
    public String getPassIf() {
        if (passIf != null) {
            return passIf;
        }
        return when != null ? when : condition;
    }

    /**
     * @deprecated renamed to {@link #getPassIf()}.
     */
    @Deprecated(forRemoval = true, since = "2.1.0")
    @JsonIgnore
    public String getWhen() {
        return getPassIf();
    }

    /**
     * @deprecated renamed to {@link #getPassIf()}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    @JsonIgnore
    public String getCondition() {
        return getPassIf();
    }

    private String conditionValue(String name, String value) {
        if (this.conditionProperty != null && !this.conditionProperty.equals(name)) {
            throw new IllegalArgumentException(
                "A check must set only one of `passIf`, `when` and `condition`; found `%s` and `%s`.".formatted(this.conditionProperty, name)
            );
        }
        this.conditionProperty = name;
        return value;
    }

    /**
     * The message associated with this check, will be displayed when the condition evaluates to {@code false}.
     */
    @NotEmpty
    String message;

    /**
     * Defines the style of the message displayed in the UI when the condition evaluates to {@code false}.
     */
    @Builder.Default
    Style style = Style.INFO;

    /**
     * The behavior to apply when the condition evaluates to {@code false}.
     */
    @Builder.Default
    Behavior behavior = Behavior.BLOCK_EXECUTION;

    /**
     * The visual style used to display the check fails.
     */
    public enum Style {
        /**
         * Display the message as an error.
         */
        ERROR,
        /**
         * Display the message as a success indicator.
         */
        SUCCESS,
        /**
         * Display the message as a warning.
         */
        WARNING,
        /**
         * Display the message as informational content.
         */
        INFO;
    }

    /**
     * Defines how the flow should behave when the condition evaluates to {@code false}.
     */
    public enum Behavior {
        /**
         * Block the creation of the execution.
         */
        BLOCK_EXECUTION,
        /**
         * Create the execution as failed.
         */
        FAIL_EXECUTION,
        /**
         * Create a new execution as a result of the check failing.
         */
        CREATE_EXECUTION;
    }

    /**
     * Resolves the effective behavior for a list of {@link Check}s based on priority.
     *
     * @param checks the list of checks whose behaviors are to be evaluated
     * @return the highest-priority behavior, or {@code CREATE_EXECUTION} if the list is empty or only contains nulls
     */
    public static Check.Behavior resolveBehavior(List<Check> checks) {
        if (checks == null || checks.isEmpty()) {
            return Behavior.CREATE_EXECUTION;
        }

        return checks.stream()
            .map(Check::getBehavior)
            .filter(Objects::nonNull).min(Comparator.comparingInt(Enum::ordinal))
            .orElse(Behavior.CREATE_EXECUTION);
    }

}

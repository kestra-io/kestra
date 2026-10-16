package io.kestra.core.models.flows.check;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
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

    private static final String SINGLE_CONDITION_NAME_MESSAGE = "A check must set only one of 'passIf', 'when' and 'condition'.";

    /**
     * The condition to evaluate.
     * <p>
     * The expression must render to a real boolean {@code true} or {@code false}. A value that renders
     * to anything else (for example the string {@code "true"} or {@code "yes"}, or a number) is treated
     * as a <em>failed</em> check, because evaluation uses a strict {@code Boolean.TRUE.equals(...)} test.
     * <p>
     * If the expression cannot be evaluated at all (for example an undefined variable or a syntax error),
     * the check fails safe: the execution is hard-blocked with {@link Behavior#BLOCK_EXECUTION} and
     * {@link Style#ERROR}, overriding the declared {@code behavior} and {@code style} — a broken gate must
     * never let a run through.
     */
    String passIf;

    /**
     * @deprecated Use {@link #getPassIf()} instead.
     */
    @Deprecated(forRemoval = true, since = "2.1.0")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String when;

    /**
     * @deprecated Use {@link #getPassIf()} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String condition;

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
     * The condition to evaluate, resolved from {@code passIf} or, for backward compatibility, from the
     * deprecated {@code when} and {@code condition} aliases.
     */
    public String getPassIf() {
        return passIf != null ? passIf : (when != null ? when : condition);
    }

    /**
     * @deprecated Use {@link #getPassIf()} instead.
     */
    @Deprecated(forRemoval = true, since = "2.1.0")
    @JsonIgnore
    public String getWhen() {
        return getPassIf();
    }

    /**
     * @deprecated Use {@link #getPassIf()} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    @JsonIgnore
    public String getCondition() {
        return getPassIf();
    }

    @JsonIgnore
    @AssertTrue(message = SINGLE_CONDITION_NAME_MESSAGE)
    public boolean isSingleConditionName() {
        int names = (passIf == null ? 0 : 1) + (when == null ? 0 : 1) + (condition == null ? 0 : 1);
        return names <= 1;
    }

    @JsonIgnore
    @AssertTrue(message = "must not be null")
    public boolean hasCondition() {
        return passIf != null || when != null || condition != null;
    }

    @JsonIgnore
    @AssertTrue(message = "must not be empty")
    public boolean isConditionNotEmpty() {
        String cond = getPassIf();
        return cond == null || !cond.isEmpty();
    }

    @JsonSetter("passIf")
    public void setPassIf(String passIf) {
        if (passIf != null) {
            ensureSingleConditionName("passIf", "when", when);
            ensureSingleConditionName("passIf", "condition", condition);
        }
        this.passIf = passIf;
    }

    /**
     * @deprecated Use {@link #setPassIf(String)} instead.
     */
    @Deprecated(forRemoval = true, since = "2.1.0")
    @JsonSetter("when")
    public void setWhen(String when) {
        if (when != null) {
            ensureSingleConditionName("when", "passIf", passIf);
            ensureSingleConditionName("when", "condition", condition);
        }
        this.when = when;
    }

    /**
     * @deprecated Use {@link #setPassIf(String)} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    @JsonSetter("condition")
    public void setCondition(String condition) {
        if (condition != null) {
            ensureSingleConditionName("condition", "passIf", passIf);
            ensureSingleConditionName("condition", "when", when);
        }
        this.condition = condition;
    }

    private static void ensureSingleConditionName(String name, String otherName, String other) {
        if (other != null) {
            throw new IllegalArgumentException(
                SINGLE_CONDITION_NAME_MESSAGE + " It sets both '%s' and '%s'.".formatted(otherName, name)
            );
        }
    }

    /**
     * The visual style used to display the message when the check fails.
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

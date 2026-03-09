package io.kestra.core.models.flows.check;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
     */
    @NotNull
    @NotEmpty
    String condition;
    
    /**
     * The message associated with this check, will be displayed when the condition evaluates to {@code false}.
     */
    @NotEmpty
    String message;
    
    /**
     * Defines the style of the message displayed in the UI when the condition evaluates to {@code false}.
     */
    Style style = Style.INFO;
    
    /**
     * The behavior to apply when the condition evaluates to {@code false}.
     */
    Behavior behavior = Behavior.BLOCK_EXECUTION;
    
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

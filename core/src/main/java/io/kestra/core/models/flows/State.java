package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.models.tasks.Task;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Value
@Slf4j
@Introspected
public class State {
    @NotNull
    @JsonInclude
    Type current;

    @Valid
    List<History> histories;

    public State() {
        this.current = Type.CREATED;
        this.histories = new ArrayList<>();
        this.histories.add(new History(this.current, Instant.now()));
    }

    public State(Type type) {
        this.current = type;
        this.histories = new ArrayList<>();
        this.histories.add(new History(this.current, Instant.now()));
    }

    public State(State state, Type type) {
        this.current = type;
        this.histories = state.histories;
        this.histories.add(new History(this.current, Instant.now()));
    }

    public State(Type state, State actual) {
        this.current = state;
        this.histories = new ArrayList<>(actual.histories);
        this.histories.add(new History(this.current, Instant.now()));
    }

    public State(Type type, List<History> histories) {
        this.current = type;
        this.histories = histories;
    }

    public static State of(Type state, List<History> histories) {
        State result = new State(state);

        result.histories.removeIf(history -> true);
        result.histories.addAll(histories);

        return result;
    }

    public State withState(Type state) {
        if (this.current == state) {
            log.warn("Can't change state, already {}", current);
            return this;
        }

        return new State(state, this);
    }

    public State reset() {
        return new State(
            Type.CREATED,
            List.of(this.histories.getFirst())
        );
    }

    /**
     * non-terminated execution duration is hard to provide in SQL, so we set it to null when endDate is empty
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Duration> getDuration() {
        if (this.getEndDate().isPresent()) {
            return Optional.of(Duration.between(this.getStartDate(), this.getEndDate().get()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * @return either the Duration persisted in database, or calculate it on the fly for non-terminated executions
     */
    public Duration getDurationOrComputeIt() {
        return this.getDuration().orElseGet(() -> Duration.between(this.getStartDate(), Instant.now()));
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Instant getStartDate() {
        return this.histories.getFirst().getDate();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY) // otherwise empty optional will be included as null
    public Optional<Instant> getEndDate() {
        if (!this.isTerminated() && !this.isPaused()) {
            return Optional.empty();
        }

        return Optional.of(this.histories.get(this.histories.size() - 1).getDate());
    }

    public String humanDuration() {
        try {
            return DurationFormatUtils.formatDurationHMS(getDurationOrComputeIt().toMillis());
        } catch (Throwable e) {
            return getDuration().toString();
        }
    }

    public Instant maxDate() {
        if (this.histories.isEmpty()) {
            return Instant.now();
        }

        return this.histories.get(this.histories.size() - 1).getDate();
    }

    public Instant minDate() {
        if (this.histories.isEmpty()) {
            return Instant.now();
        }

        return this.histories.getFirst().getDate();
    }

    @JsonIgnore
    public boolean isTerminated() {
        return this.current.isTerminated();
    }

    @JsonIgnore
    public boolean canBeRestarted() {
        return this.current.isTerminated() || this.current.isPaused();
    }

    @JsonIgnore
    public boolean isTerminatedNoFail() {
        return this.current.isTerminatedNoFail();
    }

    @JsonIgnore
    public boolean isRunning() {
        return this.current.isRunning();
    }

    @JsonIgnore
    public boolean isCreated() {
        return this.current.isCreated();
    }

    @JsonIgnore
    public static Type[] runningTypes() {
        return Arrays.stream(Type.values())
            .filter(type -> type.isRunning() || type.isCreated())
            .toArray(Type[]::new);
    }

    @JsonIgnore
    public boolean isFailed() {
        return this.current.isFailed();
    }

    @JsonIgnore
    public boolean isPaused() {
        return this.current.isPaused();
    }

    @JsonIgnore
    public boolean isBreakpoint() {
        return this.current.isBreakpoint();
    }

    @JsonIgnore
    public boolean isQueued() {
        return this.current.isQueued();
    }

    @JsonIgnore
    public boolean isRetrying() {
        return this.current.isRetrying();
    }

    @JsonIgnore
    public boolean isSuccess() {
        return this.current.isSuccess();
    }

    @JsonIgnore
    public boolean isRestartable() {
        return this.current.isFailed() || this.isPaused();
    }

    @JsonIgnore
    public boolean isResumable() {
        return this.current.isPaused() || this.current.isRetrying() || this.current.isCreated();
    }

    /**
     * Checks whether the state is restarted after being paused.
     *
     * @return {@code true} if resuming. Otherwise {@code false}.
     */
    @JsonIgnore
    public boolean isResumingAfterPause() {
        if (!this.current.equals(Type.RESTARTED) || this.histories.size() < 2) {
            return false;
        }
        return this.histories.get(this.histories.size() - 2).state.isPaused();
    }

    /**
     * Return true if the execution has failed, then was restarted.
     * This is to disambiguate between a RESTARTED after PAUSED and RESTARTED after FAILED state.
     */
    public boolean failedThenRestarted() {
       return this.current ==  Type.RESTARTED && this.histories.get(this.histories.size() - 2).state.isFailed();
    }

    @Introspected
    public enum Type {
        CREATED,
        SUBMITTED,
        RUNNING,
        PAUSED,
        RESTARTED,
        KILLING,
        SUCCESS,
        WARNING,
        FAILED,
        KILLED,
        CANCELLED,
        QUEUED,
        RETRYING,
        RETRIED,
        SKIPPED,
        BREAKPOINT,
        RESUBMITTED;

        public boolean isTerminated() {
            return this == Type.FAILED || this == Type.WARNING || this == Type.SUCCESS || this == Type.KILLED || this == Type.CANCELLED || this == Type.RETRIED || this == Type.SKIPPED || this == Type.RESUBMITTED;
        }

        public boolean isTerminatedNoFail() {
            return this == Type.WARNING || this == Type.SUCCESS || this == Type.RETRIED || this == Type.SKIPPED || this == Type.RESUBMITTED;
        }

        public boolean isCreated() {
            return this == Type.CREATED || this == Type.RESTARTED;
        }

        public boolean isRunning() {
            return this == Type.RUNNING || this == Type.KILLING;
        }

        public boolean onlyRunning() {
            return this == Type.RUNNING;
        }

        public boolean isFailed() {
            return this == Type.FAILED;
        }

        public boolean isPaused() {
            return this == Type.PAUSED;
        }

        public boolean isBreakpoint() {
            return this == Type.BREAKPOINT;
        }

        public boolean isRetrying() {
            return this == Type.RETRYING || this == Type.RETRIED;
        }

        public boolean isSuccess() {
            return this == Type.SUCCESS;
        }

        public boolean isKilled(){
            return this == Type.KILLED;
        }

        public boolean isQueued(){
            return this == Type.QUEUED;
        }

        /**
         * @return states that are terminal to an execution
         */
        public static List<Type> terminatedTypes() {
            return Stream.of(Type.values()).filter(type -> type.isTerminated()).toList();
        }

        /**
         * Compute the final 'failure' of a task depending on <code>allowFailure</code> and <code>allowWarning</code>:
         * - if both are true -> SUCCESS
         * - if only <code>allowFailure</code> is true -> WARNING
         * - if none -> FAILED
         */
        public static State.Type fail(Task task) {
            return task.isAllowFailure() ? (task.isAllowWarning() ? State.Type.SUCCESS : State.Type.WARNING) : State.Type.FAILED;
        }
    }

    @Value
    public static class History {
        @NotNull
        Type state;

        @NotNull
        Instant date;
    }
}

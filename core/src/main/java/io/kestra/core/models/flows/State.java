package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
            log.warn("Can't change state, already " + current);
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

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Duration getDuration() {
        return Duration.between(
            this.histories.getFirst().getDate(),
            this.histories.size() > 1 ? this.histories.get(this.histories.size() - 1).getDate() : Instant.now()
        );
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
            return DurationFormatUtils.formatDurationHMS(getDuration().toMillis());
        } catch (Throwable e) {
            return getDuration().toString();
        }
    }

    public Instant maxDate() {
        if (this.histories.size() == 0) {
            return Instant.now();
        }

        return this.histories.get(this.histories.size() - 1).getDate();
    }

    public Instant minDate() {
        if (this.histories.size() == 0) {
            return Instant.now();
        }

        return this.histories.getFirst().getDate();
    }

    @JsonIgnore
    public boolean isTerminated() {
        return this.current.isTerminated();
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

    @Introspected
    public enum Type {
        CREATED,
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
        SKIPPED;

        public boolean isTerminated() {
            return this == Type.FAILED || this == Type.WARNING || this == Type.SUCCESS || this == Type.KILLED || this == Type.CANCELLED || this == Type.RETRIED || this == Type.SKIPPED;
        }

        public boolean isTerminatedNoFail() {
            return this == Type.WARNING || this == Type.SUCCESS || this == Type.RETRIED || this == Type.SKIPPED;
        }

        public boolean isCreated() {
            return this == Type.CREATED || this == Type.RESTARTED;
        }

        public boolean isRunning() {
            return this == Type.RUNNING || this == Type.KILLING;
        }

        public boolean isFailed() {
            return this == Type.FAILED;
        }

        public boolean isPaused() {
            return this == Type.PAUSED;
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

        public static List<Type> terminatedTypes() {
            return Stream.of(Type.values()).filter(type -> type.isTerminated()).toList();
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

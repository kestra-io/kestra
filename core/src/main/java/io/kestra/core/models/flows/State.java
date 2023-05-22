package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Duration getDuration() {
        return Duration.between(
            this.histories.get(0).getDate(),
            this.histories.size() > 1 ? this.histories.get(this.histories.size() - 1).getDate() : Instant.now()
        );
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Instant getStartDate() {
        return this.histories.get(0).getDate();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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

    @JsonIgnore
    public boolean isTerminated() {
        return this.current.isTerminated();
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
    public boolean isRestartable() {
        return this.current.isFailed() || this.isPaused();
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
        KILLED;

        public boolean isTerminated() {
            return this == Type.FAILED || this == Type.WARNING || this == Type.SUCCESS || this == Type.KILLED;
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
    }

    @Value
    public static class History {
        @NotNull
        Type state;

        @NotNull
        Instant date;
    }
}

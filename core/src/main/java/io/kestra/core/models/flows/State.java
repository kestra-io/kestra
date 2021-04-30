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
    private Type current;

    @Valid
    private List<History> histories;

    public State() {
        this.current = Type.CREATED;
        this.histories = new ArrayList<>();
        this.histories.add(new History(this.current, Instant.now()));
    }

    public State(Type state, State actual) {
        this.current = state;
        this.histories = actual.histories;
        this.histories.add(new History(this.current, Instant.now()));
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
        if (!this.isTerninated()) {
            return Optional.empty();
        }

        return Optional.of(this.histories.get(this.histories.size() - 1).getDate());
    }

    public String humanDuration() {
        return DurationFormatUtils.formatDurationHMS(getDuration().toMillis());
    }

    @JsonIgnore
    public boolean isTerninated() {
        return this.current.isTerninated();
    }

    @JsonIgnore
    public boolean isRunning() {
        return this.current.isRunning();
    }

    @JsonIgnore
    public static Type[] runningTypes() {
        return Arrays.stream(Type.values())
            .filter(Type::isRunning)
            .toArray(Type[]::new);
    }

    @JsonIgnore
    public boolean isFailed() {
        return this.current.isFailed();
    }

    @Introspected
    public enum Type {
        CREATED,
        RUNNING,
        RESTARTED,
        KILLING,
        SUCCESS,
        WARNING,
        FAILED,
        KILLED;

        public boolean isTerninated() {
            return this == Type.FAILED || this == Type.WARNING || this == Type.SUCCESS || this == Type.KILLED;
        }

        public boolean isRunning() {
            return this == Type.RUNNING || this == Type.RESTARTED || this == Type.KILLING;
        }

        public boolean isFailed() {
            return this == Type.FAILED;
        }
    }

    @Value
    public static class History {
        @NotNull
        private Type state;

        @NotNull
        private Instant date;
    }
}

package org.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Value
@Slf4j
public class State {
    @NotNull
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
        String duration = getDuration()
            .toString()
            .substring(2)
            .replaceAll("(\\d[HMS])(?!$)", " $1 ")
            .toLowerCase();

        return (duration.substring(0, duration.length() - 4) + "s").trim();
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
    public boolean isFailed() {
        return this.current.isFailed();
    }

    public enum Type {
        CREATED,
        RUNNING,
        RESTARTED,
        SUCCESS,
        FAILED;

        public boolean isTerninated() {
            return this == Type.FAILED || this == Type.SUCCESS;
        }

        public boolean isRunning() {
            return this == Type.RUNNING || this == Type.RESTARTED;
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

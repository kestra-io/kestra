package org.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Value
public class State {
    private Type current;

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
            throw new IllegalStateException("Can't change state, already " + current);
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
        return this.current == Type.FAILED || this.current == Type.SUCCESS;
    }

    @JsonIgnore
    public boolean isRunning() {
        return this.current == Type.RUNNING;
    }

    @JsonIgnore
    public boolean isFailed() {
        return this.current == Type.FAILED;
    }

    public enum Type {
        CREATED,
        RUNNING,
        SUCCESS,
        FAILED,
    }

    @Value
    public static class History {
        private Type state;
        private Instant date;
    }
}

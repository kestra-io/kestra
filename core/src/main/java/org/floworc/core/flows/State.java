package org.floworc.core.flows;

import lombok.Value;
import org.floworc.core.executions.TaskRun;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        return new State(state, this);
    }

    public Duration duration() {
        return Duration.between(
            this.histories.get(0).getDate(),
            this.histories.size() > 1 ? this.histories.get(this.histories.size() - 1).getDate() : Instant.now()
        );
    }

    public String humanDuration() {
        String duration = duration()
            .toString()
            .substring(2)
            .replaceAll("(\\d[HMS])(?!$)", " $1 ")
            .toLowerCase();

        return duration.substring(0, duration.length() - 4) + "s";
    }

    public boolean isTerninated() {
        return this.current == Type.SKIPPED || this.current == Type.FAILED || this.current == Type.SUCCESS;
    }

    public boolean isRunning() {
        return this.current == Type.RUNNING;
    }

    public enum Type {
        CREATED,
        RUNNING,
        SUCCESS,
        FAILED,
        SKIPPED,
        PAUSED;
    }

    @Value
    public static class History {
        private Type state;
        private Instant date;
    }
}

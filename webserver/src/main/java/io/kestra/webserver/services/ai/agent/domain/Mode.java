package io.kestra.webserver.services.ai.agent.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The three conversation modes. Modes are not separate code paths: each is a profile over the one
 * orchestrator loop, selecting a system-prompt persona, a tool allow-list and a write policy.
 *
 * <ul>
 *     <li>{@link #ASK} — read + (non-mutating) authoring tools only; never mutates.</li>
 *     <li>{@link #EDIT} — read + mutation of the in-focus artefact; each write is confirmed.</li>
 *     <li>{@link #PLAN} — cumulative over Edit and adds act tools; multi-step, confirmed per step.</li>
 * </ul>
 */
public enum Mode {
    ASK,
    EDIT,
    PLAN;

    @JsonCreator
    public static Mode fromString(final String value) {
        if (value == null) {
            return null;
        }
        return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

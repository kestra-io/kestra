package io.kestra.webserver.services.ai.agent.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

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
public enum AgentMode {
    ASK,
    EDIT,
    PLAN;

    /** The tool families this mode may use — cumulative: Ask ⊂ Edit ⊂ Plan. */
    public Set<AgentToolFamily> allowedToolFamilies() {
        return switch (this) {
            case ASK -> EnumSet.of(AgentToolFamily.READ);
            case EDIT -> EnumSet.of(AgentToolFamily.READ, AgentToolFamily.MUTATE);
            case PLAN -> EnumSet.of(AgentToolFamily.READ, AgentToolFamily.MUTATE, AgentToolFamily.ACT);
        };
    }

    @JsonCreator
    public static AgentMode fromString(final String value) {
        if (value == null) {
            return null;
        }
        return AgentMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

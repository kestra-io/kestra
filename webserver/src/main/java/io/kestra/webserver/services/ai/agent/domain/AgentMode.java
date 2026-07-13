package io.kestra.webserver.services.ai.agent.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import io.kestra.core.utils.Enums;

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
    ASK(EnumSet.of(AgentToolFamily.READ)),
    EDIT(EnumSet.of(AgentToolFamily.READ, AgentToolFamily.MUTATE)),
    PLAN(EnumSet.of(AgentToolFamily.READ, AgentToolFamily.MUTATE, AgentToolFamily.ACT));

    /** The tool families this mode may use — cumulative: Ask ⊂ Edit ⊂ Plan. */
    private final Set<AgentToolFamily> allowedToolFamilies;

    AgentMode(final Set<AgentToolFamily> allowedToolFamilies) {
        this.allowedToolFamilies = Collections.unmodifiableSet(allowedToolFamilies);
    }

    public Set<AgentToolFamily> allowedToolFamilies() {
        return allowedToolFamilies;
    }

    @JsonCreator
    public static AgentMode fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, AgentMode.class);
    }
}

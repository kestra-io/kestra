package io.kestra.core.ai.agent.models;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * The three conversation modes. Modes are not separate code paths: each is a profile over the one
 * orchestrator loop, selecting a system-prompt persona, a tool allow-list and a write policy.
 *
 * <ul>
 * <li>{@link #ASK} — read + (non-mutating) authoring tools only; never mutates.</li>
 * <li>{@link #PLAN} — read + mutation of the in-focus artefact, planned and carried out step by step.</li>
 * <li>{@link #EDIT} — cumulative over Plan and adds act tools (e.g. restarting an execution); each action is confirmed.</li>
 * </ul>
 */
public enum AgentMode {
    ASK(EnumSet.of(AgentToolFamily.READ)),
    PLAN(EnumSet.of(AgentToolFamily.READ, AgentToolFamily.MUTATE)),
    EDIT(EnumSet.of(AgentToolFamily.READ, AgentToolFamily.MUTATE, AgentToolFamily.ACT));

    /** The tool families this mode may use — cumulative: Ask ⊂ Plan ⊂ Edit. */
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

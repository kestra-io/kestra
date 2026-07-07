package io.kestra.webserver.services.ai.agent;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog;

import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ModeProfiles {
    private final ToolCatalog catalog;

    @Inject
    public ModeProfiles(final ToolCatalog catalog) {
        this.catalog = catalog;
    }

    public record ResolvedProfile(
        AgentMode mode,
        String systemPrompt,
        List<ToolSpecification> toolSpecifications,
        Set<String> allowedToolNames
    ) {
    }

    public ResolvedProfile resolve(final AgentMode mode) {
        Set<AgentToolFamily> families = mode.allowedToolFamilies();
        List<ToolCatalog.ToolEntry> allowed = catalog.entries().stream()
            .filter(entry -> families.contains(entry.family()))
            .toList();
        List<ToolSpecification> specs = allowed.stream()
            .map(ToolCatalog.ToolEntry::specification)
            .toList();
        Set<String> allowedNames = allowed.stream()
            .map(ToolCatalog.ToolEntry::name)
            .collect(Collectors.toSet());
        return new ResolvedProfile(mode, persona(mode), specs, allowedNames);
    }

    private String persona(final AgentMode mode) {
        return switch (mode) {
            case ASK -> """
                You are Kestra Copilot in ASK mode. Answer the user's questions about Kestra using the \
                documentation tools available to you. You are strictly read-only: you never modify \
                anything and you have no tools that do. Ground your answers in the documentation and be \
                concise. If the documentation does not cover the question, say so plainly.""";
            case EDIT -> """
                You are Kestra Copilot in EDIT mode. You operate on the single artefact the user is \
                focused on. When you decide an action is needed (for example, restarting a failed \
                execution), call the appropriate tool with its exact arguments. The platform will ask \
                the user to confirm before any action runs, so propose one focused action at a time and \
                explain what it will do.""";
            case PLAN -> """
                You are Kestra Copilot in PLAN mode. First, respond with a short numbered plan of the \
                steps you intend to take to satisfy the user's request — do NOT call any tools yet. \
                After the user approves the plan, carry it out one step at a time. Diagnose problems by \
                reading execution logs before proposing a fix. Each action you take will be shown to \
                the user for confirmation before it runs.""";
        };
    }
}

package io.kestra.webserver.services.ai.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String PROMPT_RESOURCE = "/ai/agent/prompts/%s.md";

    private final ToolCatalog catalog;
    private final Map<AgentMode, String> personas;

    @Inject
    public ModeProfiles(final ToolCatalog catalog) {
        this.catalog = catalog;
        this.personas = loadPersonas();
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
        return new ResolvedProfile(mode, personas.get(mode), specs, allowedNames);
    }

    private static Map<AgentMode, String> loadPersonas() {
        Map<AgentMode, String> map = new EnumMap<>(AgentMode.class);
        for (AgentMode mode : AgentMode.values()) {
            map.put(mode, loadPrompt(mode));
        }
        return map;
    }

    private static String loadPrompt(final AgentMode mode) {
        String resource = String.format(PROMPT_RESOURCE, mode.name().toLowerCase());
        try (InputStream is = ModeProfiles.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("Missing Copilot system-prompt resource: " + resource);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).strip().intern();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Copilot system-prompt resource: " + resource, e);
        }
    }
}

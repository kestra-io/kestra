package io.kestra.webserver.services.ai.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.tool.AgentToolPermissionEvaluator;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ModeProfiles {
    private static final String PROMPT_RESOURCE = "/ai/agent/prompts/%s.md";
    private static final String COMMON_RESOURCE = "/ai/agent/prompts/common.md";

    private final ToolCatalog catalog;
    private final AgentToolPermissionEvaluator permissionEvaluator;
    private final Map<AgentMode, String> personas;
    private final String commonPrompt;

    @Inject
    public ModeProfiles(final ToolCatalog catalog, final AgentToolPermissionEvaluator permissionEvaluator) {
        this.catalog = catalog;
        this.permissionEvaluator = permissionEvaluator;
        this.personas = loadPersonas();
        this.commonPrompt = loadCommonPrompt();
    }

    /**
     * @param systemPrompt the mode persona (or, in EE, a custom per-provider prompt substituted for it).
     * @param commonPrompt the shared guidance appended to every prompt regardless of mode; may be blank.
     */
    public record ResolvedProfile(
        AgentMode mode,
        String systemPrompt,
        String commonPrompt,
        List<ToolSpecification> toolSpecifications,
        Set<String> allowedToolNames) {
    }

    /**
     * Resolves the profile for a turn: the system prompt for the given mode plus the set of tools
     * advertised to the model, filtered to the mode's tool families and coarsely to the tools the caller
     * is permitted to use.
     * <p>
     * The permission filter applied here is a UX and token-saving optimisation only; authoritative
     * enforcement happens per call in {@link ToolCatalog#dispatch}.
     *
     * @param mode the conversation mode whose tool families and persona apply.
     * @param tenant the tenant the turn runs in, used for the coarse permission check.
     * @param principal the caller on whose behalf tools are evaluated, or {@code null} in OSS.
     * @return the resolved profile: mode, system prompt, advertised tool specifications and allowed tool names.
     */
    public ResolvedProfile resolve(final AgentMode mode, final String tenant, @Nullable final AgentPrincipal principal) {
        Set<AgentToolFamily> families = mode.allowedToolFamilies();
        List<ToolCatalog.ToolEntry> allowed = catalog.entries().stream()
            // Authoring tools are non-mutating drafts: advertised in every mode, even Ask.
            .filter(entry -> entry.isAuthoring() || families.contains(entry.family()))
            .filter(
                entry -> !entry.isPermissionEvaluated()
                    || permissionEvaluator.isAllowed(entry, tenant, principal)
            )
            .toList();
        List<ToolSpecification> specs = allowed.stream()
            .map(ToolCatalog.ToolEntry::specification)
            .toList();
        Set<String> allowedNames = allowed.stream()
            .map(ToolCatalog.ToolEntry::name)
            .collect(Collectors.toSet());
        return new ResolvedProfile(mode, personas.get(mode), commonPrompt, specs, allowedNames);
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

    /**
     * Loads the shared prompt appended to every mode's prompt. Optional: an absent resource yields an
     * empty string (nothing is appended) rather than failing Copilot initialization.
     */
    private static String loadCommonPrompt() {
        try (InputStream is = ModeProfiles.class.getResourceAsStream(COMMON_RESOURCE)) {
            if (is == null) {
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).strip().intern();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Copilot common prompt resource: " + COMMON_RESOURCE, e);
        }
    }
}

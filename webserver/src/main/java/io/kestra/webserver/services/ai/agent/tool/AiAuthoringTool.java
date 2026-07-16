package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.ArtefactKind;

/**
 * An LLM-backed authoring delegate (sub-agent) that returns a validated, <em>unpersisted</em>
 * draft. Authoring tools are non-mutating: they carry no family and no write policy, and they are
 * advertised in every mode. Persisting a draft is a separate, confirmation-gated tool.
 */
public interface AiAuthoringTool extends AiTool {
    /** The artefact this tool authors. */
    ArtefactKind artefact();
}

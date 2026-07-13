package io.kestra.webserver.services.ai.agent.tool;

/**
 * Common contract of everything the tool catalog can register: the object carrying the single
 * {@code @Tool}-annotated method the model-facing specification is derived from.
 *
 * <p>
 * OSS tools carry no authorization logic (mirroring OSS controllers, which have none). RBAC lives
 * entirely in EE: an EE {@code @Replaces} subclass overrides the same {@code @Tool} method to check
 * the caller's grants, then delegates to {@code super}.
 * </p>
 */
public interface AiTool {
    default Object toolInstance() {
        return this;
    }
}

package io.kestra.webserver.services.ai.agent.tool;

/**
 * Common contract of everything the tool catalog can register: the object carrying the single
 * {@code @Tool}-annotated method the model-facing specification is derived from.
 *
 * <p>
 * Tools here carry no authorization logic. A replacement bean can override the same {@code @Tool}
 * method to check the caller's access, then delegate to {@code super}.
 * </p>
 */
public interface AiTool {
}

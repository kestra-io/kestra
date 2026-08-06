package io.kestra.webserver.errors;

/**
 * Declares request paths whose errors are governed by a specification other than RFC 9457, and which must
 * therefore never be served a {@link ProblemDetail} — SCIM mandates {@code application/scim+json} (RFC 7644),
 * the Model Context Protocol mandates the JSON-RPC 2.0 envelope.
 *
 * <p>A bean rather than a constant so each distribution declares the paths it owns: the Model Context Protocol
 * endpoints are Open Source, SCIM is Enterprise.
 */
public interface ProblemFormatExclusion {
    boolean excludes(String path);
}

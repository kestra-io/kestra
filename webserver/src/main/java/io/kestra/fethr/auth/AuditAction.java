package io.kestra.fethr.auth;

import java.util.Arrays;
import java.util.Optional;

/**
 * The inventory of audited user-activity actions. Each action carries its {@link AuditCategory}, a stable
 * machine {@code code} (e.g. {@code login}), and a short and long human description, so the categorisation lives
 * in the backend: the frontend renders the descriptions and (later) filters by category then action. Mirrors
 * the categorised audit-action inventory of the Plane app; the exact codes and labels are ours.
 *
 * <p>
 * {@code LOGIN} comes from the Keycloak user event log; {@code LOGOUT} and {@code PASSWORD_CHANGED} come from
 * the admin event log (server-managed sign-out and admin-driven password reset), see the audit converters.
 * </p>
 */
public enum AuditAction {
    LOGIN(AuditCategory.USER, "login", "Signed in", "User signed in"),
    LOGOUT(AuditCategory.USER, "logout", "Signed out", "User signed out"),
    PASSWORD_CHANGED(AuditCategory.USER, "password_changed", "Changed password", "User changed their password");

    private final AuditCategory category;
    private final String code;
    private final String shortDescription;
    private final String longDescription;

    AuditAction(AuditCategory category, String code, String shortDescription, String longDescription) {
        this.category = category;
        this.code = code;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getLongDescription() {
        return longDescription;
    }

    /**
     * Resolves an action from its machine {@code code} (e.g. {@code login}), empty when none matches.
     */
    public static Optional<AuditAction> fromCode(String code) {
        return Arrays.stream(values()).filter(action -> action.code.equals(code)).findFirst();
    }
}

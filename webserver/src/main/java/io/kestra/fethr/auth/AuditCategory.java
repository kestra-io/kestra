package io.kestra.fethr.auth;

/**
 * The category (domain) a user-activity audit action belongs to, mirroring the categorised audit-domain
 * inventory of the Plane app. Today every audited action is a {@link #USER} action (sign in / out / password
 * change); other categories (flow, secret, credential, ...) are added as their actions start being audited.
 */
public enum AuditCategory {
    USER("user");

    private final String code;

    AuditCategory(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

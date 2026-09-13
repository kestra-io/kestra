package io.kestra.fethr.auth;

/**
 * The atomic {@code entity.action} permissions, each of which is a realm role in Keycloak.
 *
 * <p>
 * Keycloak is the source of truth for who holds what; this enum is the catalogue, so the permission
 * API can serialize it and the UI never hard-codes the list. {@link Role} holds the composites that
 * aggregate these.
 *
 * <p>
 * Per-endpoint {@code @Secured} checks reference {@link Names} rather than these constants, because
 * an annotation cannot call {@link #getValue()}. Declared grouped by resource, and within a resource
 * read before write before destructive, which is also the order the role matrix displays.
 *
 * <p>
 * A permission declared here that has no matching realm role simply refuses every request that needs
 * it, since nobody can hold what does not exist. That is the intended failure: adding the atom here
 * and granting it in Keycloak are deliberately separate steps.
 */
public enum Permission {
    FLOW_READ(Names.FLOW_READ, Action.READ),
    FLOW_EXPORT(Names.FLOW_EXPORT, Action.READ),
    FLOW_CREATE(Names.FLOW_CREATE, Action.WRITE),
    FLOW_UPDATE(Names.FLOW_UPDATE, Action.WRITE),
    FLOW_EXECUTE(Names.FLOW_EXECUTE, Action.WRITE),
    FLOW_DELETE(Names.FLOW_DELETE, Action.DESTRUCTIVE),

    EXECUTION_READ(Names.EXECUTION_READ, Action.READ),
    EXECUTION_CREATE(Names.EXECUTION_CREATE, Action.WRITE),
    EXECUTION_RESTART(Names.EXECUTION_RESTART, Action.WRITE),
    EXECUTION_REPLAY(Names.EXECUTION_REPLAY, Action.WRITE),
    EXECUTION_KILL(Names.EXECUTION_KILL, Action.DESTRUCTIVE),

    TRIGGER_READ(Names.TRIGGER_READ, Action.READ),
    TRIGGER_WRITE(Names.TRIGGER_WRITE, Action.DESTRUCTIVE),

    LOG_READ(Names.LOG_READ, Action.READ),
    LOG_DELETE(Names.LOG_DELETE, Action.DESTRUCTIVE),

    NAMESPACE_READ(Names.NAMESPACE_READ, Action.READ),
    NAMESPACE_WRITE(Names.NAMESPACE_WRITE, Action.WRITE),

    KV_READ(Names.KV_READ, Action.READ),
    KV_WRITE(Names.KV_WRITE, Action.WRITE),

    SECRET_READ(Names.SECRET_READ, Action.READ),
    SECRET_WRITE(Names.SECRET_WRITE, Action.DESTRUCTIVE),

    TABLE_READ(Names.TABLE_READ, Action.READ),
    TABLE_CREATE(Names.TABLE_CREATE, Action.WRITE),
    TABLE_UPDATE(Names.TABLE_UPDATE, Action.WRITE),
    TABLE_WRITE(Names.TABLE_WRITE, Action.WRITE),
    TABLE_DELETE(Names.TABLE_DELETE, Action.DESTRUCTIVE),

    CREDENTIAL_READ(Names.CREDENTIAL_READ, Action.READ),
    CREDENTIAL_WRITE(Names.CREDENTIAL_WRITE, Action.DESTRUCTIVE),

    IMPORT_READ(Names.IMPORT_READ, Action.READ),
    IMPORT_CREATE(Names.IMPORT_CREATE, Action.WRITE),
    IMPORT_UPDATE(Names.IMPORT_UPDATE, Action.WRITE),
    IMPORT_DELETE(Names.IMPORT_DELETE, Action.DESTRUCTIVE),

    HL7_READ(Names.HL7_READ, Action.READ),
    HL7_WRITE(Names.HL7_WRITE, Action.DESTRUCTIVE),

    TEMPLATE_READ(Names.TEMPLATE_READ, Action.READ),
    TEMPLATE_WRITE(Names.TEMPLATE_WRITE, Action.WRITE),

    DASHBOARD_READ(Names.DASHBOARD_READ, Action.READ),
    DASHBOARD_WRITE(Names.DASHBOARD_WRITE, Action.WRITE),

    PLUGIN_READ(Names.PLUGIN_READ, Action.READ),

    AI_CONFIG_WRITE(Names.AI_CONFIG_WRITE, Action.DESTRUCTIVE),

    USER_READ(Names.USER_READ, Action.READ),
    USER_CREATE(Names.USER_CREATE, Action.WRITE),
    USER_UPDATE(Names.USER_UPDATE, Action.DESTRUCTIVE),
    USER_DELETE(Names.USER_DELETE, Action.DESTRUCTIVE),

    INVITATION_READ(Names.INVITATION_READ, Action.READ),
    INVITATION_CREATE(Names.INVITATION_CREATE, Action.WRITE),
    INVITATION_REVOKE(Names.INVITATION_REVOKE, Action.DESTRUCTIVE),

    SETTING_READ(Names.SETTING_READ, Action.READ),
    SETTING_WRITE(Names.SETTING_WRITE, Action.WRITE),

    ORGANISATION_READ(Names.ORGANISATION_READ, Action.READ),
    ORGANISATION_UPDATE(Names.ORGANISATION_UPDATE, Action.WRITE),
    ORGANISATION_DELETE(Names.ORGANISATION_DELETE, Action.DESTRUCTIVE),
    ORGANISATION_TRANSFER_OWNERSHIP(Names.ORGANISATION_TRANSFER_OWNERSHIP, Action.DESTRUCTIVE);

    private final String value;
    private final Action action;

    Permission(String value, Action action) {
        this.value = value;
        this.action = action;
    }

    public String getValue() {
        return value;
    }

    public Action getAction() {
        return action;
    }

    /** The entity the permission belongs to, i.e. the key prefix ({@code flow} for {@code flow.read}). */
    public String getResource() {
        return value.substring(0, value.indexOf('.'));
    }

    /** Display order across the whole catalogue (the declaration order: by resource, then read/write/destructive). */
    public int getOrder() {
        return ordinal();
    }

    /**
     * Compile-time {@code String} constants mirroring {@link Permission#getValue()}, for use in
     * {@code @Secured} (the annotation cannot reference an enum value via a method call). The enum
     * constructors reference these, so each realm role name is declared exactly once.
     */
    public interface Names {
        String ORGANISATION_READ = "organisation.read";
        String ORGANISATION_UPDATE = "organisation.update";
        String ORGANISATION_DELETE = "organisation.delete";
        String ORGANISATION_TRANSFER_OWNERSHIP = "organisation.transfer_ownership";

        String FLOW_READ = "flow.read";
        String FLOW_EXPORT = "flow.export";
        String FLOW_CREATE = "flow.create";
        String FLOW_UPDATE = "flow.update";
        String FLOW_DELETE = "flow.delete";
        String FLOW_EXECUTE = "flow.execute";

        String EXECUTION_READ = "execution.read";
        String EXECUTION_CREATE = "execution.create";
        String EXECUTION_KILL = "execution.kill";
        String EXECUTION_RESTART = "execution.restart";
        String EXECUTION_REPLAY = "execution.replay";

        String KV_READ = "kv.read";
        String KV_WRITE = "kv.write";

        String SECRET_READ = "secret.read";
        String SECRET_WRITE = "secret.write";

        String TABLE_READ = "table.read";
        String TABLE_CREATE = "table.create";
        String TABLE_UPDATE = "table.update";
        String TABLE_WRITE = "table.write";
        String TABLE_DELETE = "table.delete";

        String CREDENTIAL_READ = "credential.read";
        String CREDENTIAL_WRITE = "credential.write";

        // Migrating a vendor's configuration into Fethr. These are new atoms: until the matching
        // roles exist in the Keycloak realm and are granted to the relevant composites, every
        // imports endpoint will refuse the request.
        String IMPORT_READ = "import.read";
        String IMPORT_CREATE = "import.create";
        String IMPORT_UPDATE = "import.update";
        String IMPORT_DELETE = "import.delete";

        // Declared like the import atoms above; which realm roles carry them is decided in Keycloak, not here.
        String HL7_READ = "hl7.read";
        String HL7_WRITE = "hl7.write";

        String USER_READ = "user.read";
        String USER_CREATE = "user.create";
        String USER_UPDATE = "user.update";
        String USER_DELETE = "user.delete";

        String INVITATION_READ = "invitation.read";
        String INVITATION_CREATE = "invitation.create";
        String INVITATION_REVOKE = "invitation.revoke";

        String LOG_READ = "log.read";
        String LOG_DELETE = "log.delete";

        String NAMESPACE_READ = "namespace.read";
        String NAMESPACE_WRITE = "namespace.write";

        String TRIGGER_READ = "trigger.read";
        String TRIGGER_WRITE = "trigger.write";

        String TEMPLATE_READ = "template.read";
        String TEMPLATE_WRITE = "template.write";

        String DASHBOARD_READ = "dashboard.read";
        String DASHBOARD_WRITE = "dashboard.write";

        String SETTING_READ = "setting.read";
        String SETTING_WRITE = "setting.write";

        String PLUGIN_READ = "plugin.read";

        // Writes the provider credential as a Kestra secret, hence destructive like secret.write.
        String AI_CONFIG_WRITE = "ai.config.write";
    }
}

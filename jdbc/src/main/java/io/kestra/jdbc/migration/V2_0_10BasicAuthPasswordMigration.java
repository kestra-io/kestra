package io.kestra.jdbc.migration;

import java.io.IOException;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.migration.MigrationScript;
import io.kestra.core.utils.AuthUtils;
import io.kestra.jdbc.JdbcJsonbUtils;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Migrates the stored BasicAuth password from plain SHA-512 to bcrypt-wrapped SHA-512.
 *
 * <p>The {@code settings} table stores the BasicAuth credentials under key
 * {@code kestra.server.basic-auth} as a JSON object of the form:
 * <pre>{@code
 * {
 *   "key":   "kestra.server.basic-auth",
 *   "value": { "salt": "...", "username": "...", "password": "<sha512-hex>" }
 * }
 * }</pre>
 *
 * <p>Because the SHA-512 digest is one-way, the migration cannot recover the original
 * plaintext. Instead it wraps the existing digest in bcrypt: {@code bcrypt(sha512_hex)}.
 * The auth code uniformly computes {@code bcrypt(sha512(salt|password))} for every
 * verification, so migrated and newly-set passwords follow exactly the same path with
 * no dual-format detection required.
 *
 * <p>Idempotency: if the stored password already starts with {@code $2} (a bcrypt
 * modular-crypt string) the migration is skipped silently.
 */
@Slf4j
@Singleton
@JdbcRepositoryEnabled
public class V2_0_10BasicAuthPasswordMigration implements MigrationScript {

    static final String BASIC_AUTH_SETTINGS_KEY = "kestra.server.basic-auth";

    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));
    private static final ObjectMapper MAPPER = JdbcMapper.of();

    private final JooqDSLContextWrapper dslContextWrapper;

    @Inject
    public V2_0_10BasicAuthPasswordMigration(final JooqDSLContextWrapper dslContextWrapper) {
        this.dslContextWrapper = dslContextWrapper;
    }

    @Override
    public String scriptId() {
        return "2.0.10-basic-auth-password";
    }

    @Override
    public String description() {
        return "Migrate BasicAuth password hash from SHA-512 to bcrypt(SHA-512) (CWE-916 fix)";
    }

    @Override
    public String checksum() {
        // Java-only migration – no SQL resource file, checksum validation not applicable.
        return null;
    }

    @Override
    public void migrate() throws Exception {
        dslContextWrapper.transaction(configuration -> {
            Record1<Object> record = DSL.using(configuration)
                .select(VALUE_FIELD)
                .from(DSL.table("settings"))
                .where(KEY_FIELD.eq(BASIC_AUTH_SETTINGS_KEY))
                .fetchOne();

            if (record == null) {
                log.info("BasicAuth migration: no basic-auth setting found, skipping.");
                return;
            }

            String json = record.get(VALUE_FIELD, String.class);
            if (json == null || json.isBlank()) {
                log.warn("BasicAuth migration: setting row exists but value is empty, skipping.");
                return;
            }

            JsonNode root;
            try {
                root = MAPPER.readTree(json);
            } catch (IOException e) {
                // Corrupt JSON must not be silently skipped: the migration runner would mark the script
                // as applied and skip it on future restarts, leaving the password unmigrated permanently.
                // Fail hard so the operator is forced to fix the settings row.
                log.error("BasicAuth migration: failed to parse setting JSON — manual intervention required.", e);
                throw new RuntimeException("BasicAuth migration: failed to parse setting JSON", e);
            }

            // The value column stores the full Setting entity: { "key": "...", "value": { salt, username, password } }
            JsonNode valueNode = root.path("value");
            if (valueNode.isMissingNode() || !valueNode.isObject()) {
                log.warn("BasicAuth migration: expected a nested 'value' object, skipping. JSON shape: {}", root.getNodeType());
                return;
            }

            JsonNode passwordNode = valueNode.path("password");
            if (passwordNode.isMissingNode() || !passwordNode.isTextual()) {
                log.warn("BasicAuth migration: 'password' field missing or not a string, skipping.");
                return;
            }

            String storedPassword = passwordNode.asText();
            if (storedPassword.startsWith("$2")) {
                log.info("BasicAuth migration: password is already bcrypt-hashed, skipping (idempotent).");
                return;
            }

            // Wrap the existing SHA-512 digest in bcrypt — no plaintext required.
            String bcryptHash = AuthUtils.bcryptDigest(storedPassword);

            ((ObjectNode) valueNode).put("password", bcryptHash);
            String updatedJson;
            try {
                updatedJson = MAPPER.writeValueAsString(root);
            } catch (IOException e) {
                log.error("BasicAuth migration: failed to serialize updated setting JSON.", e);
                throw new RuntimeException(e);
            }

            DSL.using(configuration)
                .update(DSL.table("settings"))
                .set(VALUE_FIELD, (Object) JdbcJsonbUtils.valueOf(updatedJson))
                .where(KEY_FIELD.eq(BASIC_AUTH_SETTINGS_KEY))
                .execute();

            log.info("BasicAuth migration: password successfully upgraded to bcrypt (cost {}).", AuthUtils.BCRYPT_COST);
        });
    }
}

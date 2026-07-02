package io.kestra.jdbc.migration;

import java.util.Map;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.utils.AuthUtils;
import io.kestra.jdbc.JdbcJsonbUtils;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.kestra.jdbc.migration.V2_0_10BasicAuthPasswordMigration.BASIC_AUTH_SETTINGS_KEY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract integration tests for {@link V2_0_10BasicAuthPasswordMigration}.
 * Subclassed per JDBC backend (H2, Postgres, MySQL).
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractV2_0_10BasicAuthPasswordMigrationTest {

    private static final ObjectMapper MAPPER = JdbcMapper.of();
    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));

    private static final String SALT = "testSalt0123456789ABCDEFabcdef01";
    private static final String PASSWORD = "Password123";

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_10BasicAuthPasswordMigration migration;

    @BeforeEach
    void cleanup() {
        dslContextWrapper.transaction(configuration ->
            DSL.using(configuration)
                .deleteFrom(DSL.table("settings"))
                .where(KEY_FIELD.eq(BASIC_AUTH_SETTINGS_KEY))
                .execute()
        );
    }

    @Test
    void shouldMigrateSha512PasswordToBcrypt() throws Exception {
        // Given – insert a legacy SHA-512-hashed credential row
        String sha512 = AuthUtils.encodePassword(SALT, PASSWORD);
        insertSettingRow(SALT, "admin@kestra.io", sha512);

        // When
        migration.migrate();

        // Then – the stored password is now a bcrypt string
        String storedPassword = readStoredPassword();
        assertThat(storedPassword).startsWith("$2y$");

        // And it must still verify correctly against the original password
        assertThat(AuthUtils.matches(SALT, PASSWORD, storedPassword)).isTrue();
        assertThat(AuthUtils.matches(SALT, "WrongPassword1", storedPassword)).isFalse();
    }

    @Test
    void shouldBeIdempotent_whenAlreadyBcrypt() throws Exception {
        // Given – insert a credential that is already bcrypt-hashed
        String bcryptHash = AuthUtils.hashPassword(SALT, PASSWORD);
        insertSettingRow(SALT, "admin@kestra.io", bcryptHash);

        // When – run migration twice
        migration.migrate();
        migration.migrate();

        // Then – the stored password is still the original bcrypt hash (unchanged)
        String storedPassword = readStoredPassword();
        assertThat(storedPassword).isEqualTo(bcryptHash);
    }

    @Test
    void shouldSkipGracefully_whenNoSettingRowExists() throws Exception {
        // Given – table is empty (cleanup already removed any row)

        // When / Then – must not throw
        migration.migrate();
    }

    // --- helpers ---

    private void insertSettingRow(String salt, String username, String password) throws Exception {
        Map<String, Object> innerValue = Map.of(
            "salt", salt,
            "username", username,
            "password", password
        );
        Map<String, Object> settingJson = Map.of(
            "key", BASIC_AUTH_SETTINGS_KEY,
            "value", innerValue
        );
        String json = MAPPER.writeValueAsString(settingJson);
        dslContextWrapper.transaction(configuration ->
            DSL.using(configuration)
                .insertInto(DSL.table("settings"))
                .set(KEY_FIELD, (Object) BASIC_AUTH_SETTINGS_KEY)
                .set(VALUE_FIELD, (Object) JdbcJsonbUtils.valueOf(json))
                .execute()
        );
    }

    private String readStoredPassword() throws Exception {
        String json = dslContextWrapper.transactionResult(configuration ->
            DSL.using(configuration)
                .select(VALUE_FIELD)
                .from(DSL.table("settings"))
                .where(KEY_FIELD.eq(BASIC_AUTH_SETTINGS_KEY))
                .fetchOne(VALUE_FIELD, String.class)
        );
        assertThat(json).isNotNull();
        return MAPPER.readTree(json).path("value").path("password").asText();
    }
}

package io.kestra.repository.mysql;

import java.time.Instant;
import java.util.List;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.AbstractLogDataStoreTest;
import io.kestra.core.repositories.LogDataStoreInterface.KeyedLog;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public class MysqlLogDataStoreTest extends AbstractLogDataStoreTest {

    /**
     * The MySQL {@code timestamp} column is a generated {@code DATETIME(6)} holding the UTC wall-clock
     * (see {@code baseline-mysql.sql}), so the keyset seek binds its {@code Instant} argument as UTC and
     * compares it against that stored wall-clock. This pins the absolute meaning of {@code afterTimestamp}
     * so a driver-side timezone conversion (Connector/J converts a bound {@code OffsetDateTime} through the
     * connection timezone) cannot silently shift the boundary on a non-UTC deployment: the exact-instant
     * boundary must be strictly excluded, and one millisecond earlier must include the whole group.
     */
    @Test
    void findAfterWithoutAcl_seeksByAbsoluteUtcInstant() {
        String tenant = IdUtils.create();
        Instant at = Instant.parse("2024-06-01T10:00:00.123Z");
        logDataStore.saveBatch(List.of(
            log(Level.TRACE, "exec-offset").tenantId(tenant).timestamp(at).message("a").build(),
            log(Level.INFO, "exec-offset").tenantId(tenant).timestamp(at).message("b").build(),
            log(Level.ERROR, "exec-offset").tenantId(tenant).timestamp(at).message("c").build()
        ));

        assertThat(logDataStore.findAfterWithoutAcl(tenant, List.of(), at, null, 10)).isEmpty();

        List<KeyedLog> all = logDataStore.findAfterWithoutAcl(tenant, List.of(), at.minusMillis(1), null, 10);
        assertThat(all).extracting(k -> k.log().getMessage()).containsExactlyInAnyOrder("a", "b", "c");
    }

    private static final Field<Object> KEY = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE = DSL.field(DSL.quotedName("value"));

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    /**
     * The shipper picks the resume offset key with {@code LogPosition.max}, i.e. Java's case-sensitive
     * {@code String.compareTo}, but the {@code key} column defaults to the case-insensitive
     * {@code utf8mb4_0900_ai_ci} collation. When two same-timestamp rows carry FriendlyId keys that order one
     * way case-sensitively and the other way case-insensitively, a case-insensitive seek re-ships the row that
     * sorts after the offset — the boundary re-ship bug. The keys below are such a pair: {@code "Kx…" < "kA…"}
     * case-sensitively (so {@code "kA…"} is the persisted offset), but {@code "Kx…" > "kA…"} case-insensitively.
     */
    @Test
    void findAfterWithoutAcl_ordersKeysCaseSensitivelyLikeTheOffset() throws Exception {
        String tenant = IdUtils.create();
        Instant at = Instant.parse("2024-06-01T10:00:00.123Z");
        String suffix = IdUtils.create();
        String lowerCaseSensitive = "Kx" + suffix;
        String upperCaseSensitive = "kA" + suffix;

        insertLog(tenant, lowerCaseSensitive, at, Level.INFO, "lower");
        insertLog(tenant, upperCaseSensitive, at, Level.INFO, "upper");

        // The offset the shipper would persist: the case-sensitive max of the two keys (mirrors LogPosition.max).
        String offsetKey = lowerCaseSensitive.compareTo(upperCaseSensitive) >= 0 ? lowerCaseSensitive : upperCaseSensitive;

        assertThat(logDataStore.findAfterWithoutAcl(tenant, List.of(), at, offsetKey, 10)).isEmpty();
    }

    private void insertLog(String tenant, String key, Instant timestamp, Level level, String message) throws Exception {
        LogEntry entry = log(level, "exec-collation").tenantId(tenant).timestamp(timestamp).message(message).build();
        String value = JacksonMapper.ofJson().writeValueAsString(entry);
        dslContextWrapper.transaction(configuration ->
            DSL.using(configuration)
                .insertInto(DSL.table("logs"))
                .set(KEY, (Object) key)
                .set(VALUE, (Object) value)
                .execute()
        );
    }
}

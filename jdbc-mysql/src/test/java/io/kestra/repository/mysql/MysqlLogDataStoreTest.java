package io.kestra.repository.mysql;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.repositories.AbstractLogDataStoreTest;
import io.kestra.core.repositories.LogDataStoreInterface.KeyedLog;
import io.kestra.core.utils.IdUtils;

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
}

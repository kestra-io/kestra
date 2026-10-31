package io.kestra.runner.h2;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStateStore;
import io.kestra.repository.h2.H2Repository;
import io.kestra.repository.h2.H2RepositoryEnabled;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionDelayStateStore extends AbstractJdbcExecutionDelayStateStore {
    // Matches KestraDateTimeModule's INSTANT_WRITER, whose digits the generated "date" column stores.
    private static final DateTimeFormatter UTC_WALL_CLOCK = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSS", Locale.ROOT);

    public H2ExecutionDelayStateStore(@Named("executordelayed") H2Repository<ExecutionDelay> repository) {
        super(repository);
    }

    @Override
    protected Condition expiredCondition(Instant now) {
        // jOOQ would bind a LocalDateTime as a Timestamp via Timestamp.valueOf, which reads the JVM
        // zone, and H2 would re-render that instant in the session zone, so the cutoff shifts
        // whenever the two zones differ. The column holds zone-free digits, so compare digits.
        String cutoff = UTC_WALL_CLOCK.format(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        return DATE_FIELD.lessOrEqual(DSL.cast(DSL.val(cutoff), SQLDataType.LOCALDATETIME));
    }
}

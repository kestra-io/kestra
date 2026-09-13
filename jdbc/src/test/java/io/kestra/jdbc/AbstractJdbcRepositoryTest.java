package io.kestra.jdbc;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.TimeZone;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import io.kestra.core.repositories.ArrayListTotal;
import io.micronaut.data.model.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AbstractJdbcRepositoryTest {
    /** Zones with a non-zero, and deliberately non-integral, offset from UTC. */
    private static final List<String> NON_UTC_ZONES = List.of("Asia/Kolkata", "Europe/Berlin", "America/Los_Angeles");

    private final TestJdbcRepository repository = new TestJdbcRepository();

    private static Record weekRecord(int year, int week) {
        Field<Integer> yearField = DSL.field(DSL.name("year"), Integer.class);
        Field<Integer> weekField = DSL.field(DSL.name("week"), Integer.class);
        Record record = DSL.using(SQLDialect.H2).newRecord(yearField, weekField);
        record.set(yearField, year);
        record.set(weekField, week);
        return record;
    }

    private static Record partsRecord(int year, int month, int day, int hour, int minute) {
        Field<Integer> yearField = DSL.field(DSL.name("year"), Integer.class);
        Field<Integer> monthField = DSL.field(DSL.name("month"), Integer.class);
        Field<Integer> dayField = DSL.field(DSL.name("day"), Integer.class);
        Field<Integer> hourField = DSL.field(DSL.name("hour"), Integer.class);
        Field<Integer> minuteField = DSL.field(DSL.name("minute"), Integer.class);
        Record record = DSL.using(SQLDialect.H2).newRecord(yearField, monthField, dayField, hourField, minuteField);
        record.set(yearField, year);
        record.set(monthField, month);
        record.set(dayField, day);
        record.set(hourField, hour);
        record.set(minuteField, minute);
        return record;
    }

    /**
     * Runs {@code assertions} once per non-UTC JVM default zone, restoring the original zone
     * afterwards. The date parts handed to {@code getDate} are UTC wall-clock values, so the
     * reassembled instant must not depend on where the host happens to be.
     */
    private static void underEachNonUtcDefaultZone(Runnable assertions) {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : NON_UTC_ZONES) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertions.run();
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void shouldNotThrowWhenWeekGroupingHasIsoWeek53() {
        // Given a "week" grouping record whose ISO week is 53 (week * 7 = 371, outside the day-of-year range)
        Record record = weekRecord(2020, 53);

        // When / Then getDate must not throw a DateTimeException
        assertThatCode(() -> repository.getDate(record, "week")).doesNotThrowAnyException();
        assertThat(repository.getDate(record, "week")).isNotNull();
    }

    @Test
    void shouldNotThrowWhenWeekGroupingHasWeekZero() {
        // Given a "week" grouping record whose week is 0 (some SQL WEEK() modes return 0)
        Record record = weekRecord(2020, 0);

        // When / Then getDate must not throw a DateTimeException
        assertThatCode(() -> repository.getDate(record, "week")).doesNotThrowAnyException();
        assertThat(repository.getDate(record, "week")).isNotNull();
    }

    @Test
    void shouldReturnMondayStartOfDayInUtcWhenWeekGrouping() {
        // Given a regular week grouping record
        Record record = weekRecord(2021, 24);

        // When resolving the week bucket
        Instant date = repository.getDate(record, "week");

        // Then it is the Monday at UTC start of day
        LocalDate expectedMonday = LocalDate.ofYearDay(2021, 24 * 7)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(date).isEqualTo(expectedMonday.atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(date.atZone(ZoneOffset.UTC).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void shouldReassembleBucketsInUtcRegardlessOfDefaultZone() {
        // Given date parts extracted as UTC wall-clock values: 2026-07-29 14:30 UTC
        Record record = partsRecord(2026, 7, 29, 14, 30);

        // When / Then each bucket is anchored in UTC, whatever the host's zone is
        underEachNonUtcDefaultZone(() -> {
            assertThat(repository.getDate(record, "minute")).isEqualTo(Instant.parse("2026-07-29T14:30:00Z"));
            assertThat(repository.getDate(record, "hour")).isEqualTo(Instant.parse("2026-07-29T14:00:00Z"));
            assertThat(repository.getDate(record, "day")).isEqualTo(Instant.parse("2026-07-29T00:00:00Z"));
            assertThat(repository.getDate(record, "month")).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        });
    }

    @Test
    void shouldReassembleWeekBucketInUtcRegardlessOfDefaultZone() {
        // Given a week grouping record
        Record record = weekRecord(2021, 24);

        // When / Then the Monday start-of-day is in UTC, whatever the host's zone is
        underEachNonUtcDefaultZone(() ->
            assertThat(repository.getDate(record, "week")).isEqualTo(Instant.parse("2021-06-14T00:00:00Z"))
        );
    }

    private static final class TestJdbcRepository extends AbstractJdbcRepository<Object> {
        private TestJdbcRepository() {
            super(new JdbcTableConfig("test", Object.class, "test"), null);
        }

        @Override
        public Condition fullTextCondition(List<String> fields, String query) {
            return DSL.noCondition();
        }

        @Override
        public <R extends Record, E> ArrayListTotal<E> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper) {
            return new ArrayListTotal<>(0);
        }
    }
}

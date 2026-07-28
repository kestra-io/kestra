package io.kestra.jdbc;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final TestJdbcRepository repository = new TestJdbcRepository();

    private static Record weekRecord(int year, int week) {
        Field<Integer> yearField = DSL.field(DSL.name("year"), Integer.class);
        Field<Integer> weekField = DSL.field(DSL.name("week"), Integer.class);
        Record record = DSL.using(SQLDialect.H2).newRecord(yearField, weekField);
        record.set(yearField, year);
        record.set(weekField, week);
        return record;
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
    void shouldReturnMondayStartOfDayInDefaultZoneWhenWeekGrouping() {
        // Given a regular week grouping record
        Record record = weekRecord(2021, 24);

        // When resolving the week bucket
        Instant date = repository.getDate(record, "week");

        // Then it is the Monday at start of day, using that date's own zone offset (not the current offset)
        ZoneId zone = TimeZone.getDefault().toZoneId();
        LocalDate expectedMonday = LocalDate.ofYearDay(2021, 24 * 7)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(date).isEqualTo(expectedMonday.atStartOfDay(zone).toInstant());
        assertThat(date.atZone(zone).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
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

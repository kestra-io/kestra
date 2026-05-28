package io.kestra.jdbc;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.metrics.MetricAggregation;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.IdUtils;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.Sort.Order;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import static io.kestra.core.utils.CaseUtils.camelToSnake;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.*;

public abstract class AbstractJdbcRepository<T> {
    protected static final ObjectMapper MAPPER = JdbcMapper.of();

    protected final Class<T> cls;

    @Setter
    protected Function<Record, T> deserializer;

    @Getter
    protected final JooqDSLContextWrapper dslContextWrapper;

    @Getter
    protected Table<Record> table;

    @SuppressWarnings("unchecked")
    public AbstractJdbcRepository(
        JdbcTableConfig tableConfig,
        JooqDSLContextWrapper dslContextWrapper) {
        this.cls = (Class<T>) tableConfig.cls();
        this.dslContextWrapper = dslContextWrapper;
        this.table = DSL.table(tableConfig.table());
    }

    abstract public Condition fullTextCondition(List<String> fields, String query);

    public String key(T entity) {
        String key = entity instanceof HasUID hasUID ? hasUID.uid() : null;

        if (key != null) {
            return key;
        }

        return IdUtils.create();
    }

    @SneakyThrows
    public Map<Field<Object>, Object> persistFields(T entity) {
        Map<Field<Object>, Object> fields = HashMap.newHashMap(1);
        fields.put(VALUE_FIELD, MAPPER.writeValueAsString(entity));
        return fields;
    }

    /**
     * Fetches an entity using {@code fetcher}, or inserts a default one (via {@code defaultEntity}) if absent, then re-fetches.
     * The {@code fetcher} should use {@code FOR UPDATE} so the returned entity is locked within the caller's transaction.
     */
    public T getOrInsert(DSLContext dslContext, Supplier<Optional<T>> fetcher, Supplier<T> defaultEntity) {
        // Note: ideally, we should emit an INSERT IGNORE or ON CONFLICT DO NOTHING but H2 didn't support it.
        // So to avoid the case where no record exists and two threads insert concurrently, in H2, we select/insert and if the insert fails, select again.
        // Anyway, this would only occur once in a record lifecycle, so even if it's not elegant, it should work.
        // But as this pattern didn't work with Postgres, we emit INSERT IGNORE in Postgres and MySQL, so we're sure it works there also, and it's better than relying on exception.
        return fetcher.get().orElseGet(() -> {
            try {
                T entity = defaultEntity.get();
                Map<Field<Object>, Object> fields = this.persistFields(entity);
                var insert = dslContext
                    .insertInto(this.getTable())
                    .set(KEY_FIELD, this.key(entity))
                    .set(fields);
                if (dslContext.configuration().dialect().supports(SQLDialect.POSTGRES) || dslContext.configuration().dialect().supports(SQLDialect.MYSQL)) {
                    insert.onDuplicateKeyIgnore().execute();
                } else {
                    insert.execute();
                }
            } catch (DataAccessException e) {
                // we ignore any constraint violation
            }
            // refetch to have a lock on it
            // at this point we are sure the record is inserted so it should never throw
            return fetcher.get().orElseThrow();
        });
    }

    public int count(Condition condition) {
        return getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .selectCount()
                    .from(getTable())
                    .where(condition)
                    .fetchOne(0, Integer.class)
            );
    }

    /**
     * Do an insert or update on the table (upsert).
     * This is convenient to be fault-tolerant of possible conflict but is less performant than an UPDATE is we're sure the entity exists.
     *
     * @see #persist(T, Map)
     * @see #persist(T, DSLContext, Map)
     * @see #update(T)
     * @see #update(T, Map)
     * @see #update(T, DSLContext, Map)
     */
    public void persist(T entity) {
        this.persist(entity, null);
    }


    /**
     * Do an insert or update on the table (upsert).
     * This is convenient to be fault-tolerant of possible conflict but is less performant than an UPDATE is we're sure the entity exists.
     *
     * @see #persist(T)
     * @see #persist(T, DSLContext, Map)
     * @see #update(T)
     * @see #update(T, Map)
     * @see #update(T, DSLContext, Map)
     */
    public void persist(T entity, Map<Field<Object>, Object> fields) {
        dslContextWrapper.transaction(
            configuration -> this.persist(entity, DSL.using(configuration), fields)
        );
    }

    /**
     * Do an insert or update on the table (upsert).
     * This is convenient to be fault-tolerant of possible conflict but is less performant than an UPDATE is we're sure the entity exists.
     *
     * @see #persist(T)
     * @see #persist(T, Map)
     * @see #update(T)
     * @see #update(T, Map)
     * @see #update(T, DSLContext, Map)
     */
    public void persist(T entity, DSLContext dslContext, Map<Field<Object>, Object> fields) {
        Map<Field<Object>, Object> finalFields = fields == null ? this.persistFields(entity) : fields;

        dslContext
            .insertInto(table)
            .set(KEY_FIELD, key(entity))
            .set(finalFields)
            .onDuplicateKeyUpdate()
            .set(finalFields)
            .execute();
    }

    /**
     * Update the entity.
     * For a safer upsert approach see the corresponding <code>persist()</code> methods
     *
     * @see #update(Object, Map)
     * @see #update(Object, DSLContext, Map)
     * @see #persist(Object)
     * @see #persist(Object, Map)
     * @see #persist(Object, DSLContext, Map)
     */
    public void update(T entity) {
        this.persist(entity, null);
    }

    /**
     * Update the entity.
     * For a safer upsert approach see the corresponding <code>persist()</code> methods
     *
     * @see #update(Object)
     * @see #update(Object, DSLContext, Map)
     * @see #persist(Object)
     * @see #persist(Object, Map)
     * @see #persist(Object, DSLContext, Map)
     */
    public void update(T entity, Map<Field<Object>, Object> fields) {
        dslContextWrapper.transaction(
            configuration -> this.persist(entity, DSL.using(configuration), fields)
        );
    }

    /**
     * Update the entity.
     * For a safer upsert approach see the corresponding <code>persist()</code> methods
     *
     * @see #update(Object)
     * @see #update(Object, Map)
     * @see #persist(Object)
     * @see #persist(Object, Map)
     * @see #persist(Object, DSLContext, Map)
     */
    public void update(T entity, DSLContext dslContext, Map<Field<Object>, Object> fields) {
        Map<Field<Object>, Object> finalFields = fields == null ? this.persistFields(entity) : fields;

        dslContext
            .update(table)
            .set(finalFields)
            .where(KEY_FIELD.eq(key(entity)))
            .execute();
    }

    public int persistBatch(List<T> items) {
        return dslContextWrapper.transactionResult(configuration ->
        {
            DSLContext dslContext = DSL.using(configuration);
            var inserts = items.stream()
                .map(item -> buildInsertRequest(item, this.persistFields(item), dslContext))
                .toList();

            return Arrays.stream(dslContext.batch(inserts).execute()).sum();
        });
    }

    public int persistBatch(Map<T, Map<Field<Object>, Object>> itemWithFields) {
        return dslContextWrapper.transactionResult(configuration ->
        {
            DSLContext dslContext = DSL.using(configuration);
            var inserts = itemWithFields.entrySet()
                .stream().map(entry -> buildInsertRequest(entry.getKey(), entry.getValue(), dslContext))
                .toList();

            return Arrays.stream(dslContext.batch(inserts).execute()).sum();
        });
    }

    protected InsertOnDuplicateSetMoreStep<Record> buildInsertRequest(T entity, Map<Field<Object>, Object> fields,
        DSLContext dslContext) {

        return dslContext
            .insertInto(table)
            .set(KEY_FIELD, key(entity))
            .set(fields)
            .onDuplicateKeyUpdate()
            .set(fields);
    }

    public int delete(T entity) {
        return dslContextWrapper.transactionResult(configuration ->
        {
            return this.delete(DSL.using(configuration), entity);
        });
    }

    public int delete(DSLContext dslContext, T entity) {
        DeleteConditionStep<Record> key = dslContext
            .delete(table)
            .where(KEY_FIELD.eq(key(entity)));

        return key.execute();
    }

    public <R extends Record> T map(R record) {
        if (deserializer != null) {
            return deserializer.apply(record);
        } else {
            return this.deserialize(record.get("value", String.class));
        }
    }

    public <R extends Record> MetricAggregation mapMetricAggregation(R record, String groupByType) {
        Instant date = getDate(record, groupByType);
        return MetricAggregation
            .builder()
            .name(record.get("metric_name", String.class))
            .value(record.get("metric_value", Double.class))
            .date(date)
            .build();

    }

    public <R extends Record> Instant getDate(R record, String groupByType) {
        List<String> fields = Arrays.stream(record.fields()).map(Field::getName).toList();
        Integer minute = fields.contains("minute") ? record.get("minute", Integer.class) : 0;
        Integer hour = fields.contains("hour") ? record.get("hour", Integer.class) : 0;
        Integer day = fields.contains("day") ? record.get("day", Integer.class) : 0;
        Integer week = fields.contains("week") ? record.get("week", Integer.class) : 0;
        Integer month = fields.contains("month") ? record.get("month", Integer.class) : 0;
        Integer year = fields.contains("year") ? record.get("year", Integer.class) : 0;

        switch (groupByType) {
            case "minute" -> {
                return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TimeZone.getDefault().toZoneId()).toInstant();
            }
            case "hour" -> {
                return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, TimeZone.getDefault().toZoneId()).toInstant();
            }
            case "day" -> {
                return ZonedDateTime.of(year, month, day, 0, 0, 0, 0, TimeZone.getDefault().toZoneId()).toInstant();
            }
            case "week" -> {
                LocalDate weekDate = LocalDate.ofYearDay(year, week * 7);
                return weekDate.atStartOfDay().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toInstant(ZonedDateTime.now().getOffset());
            }
            case "month" -> {
                return ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, TimeZone.getDefault().toZoneId()).toInstant();
            }
            default -> throw new IllegalArgumentException("Invalid groupByType: " + groupByType);
        }
    }

    public T deserialize(String record) {
        try {
            return MAPPER.readValue(record, cls);
        } catch (IOException e) {
            throw new DeserializationException(e, record);
        }
    }

    public <R extends Record> Optional<T> fetchOne(Select<R> select) {
        return Optional.ofNullable(select.fetchAny())
            .map(this::map);
    }

    public <R extends Record> List<T> fetch(Select<R> select) {
        return select.fetch().map(this::map);
    }

    public List<MetricAggregation> fetchMetricStat(Select<Record> select, String groupByType) {
        return select.fetch().map(e -> this.mapMetricAggregation(e, groupByType));
    }

    abstract public <R extends Record, E> ArrayListTotal<E> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper);

    public <R extends Record> ArrayListTotal<T> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable) {
        return this.fetchPage(context, select, pageable, this::map);
    }

    @SuppressWarnings("unchecked")
    public <R extends Record> Select<R> buildQuery(DSLContext context, SelectConditionStep<R> select, String orderField) {
        return (Select<R>) context.select(DSL.asterisk())
            .from(
                this
                    .sort(select, Pageable.from(Sort.of(Order.asc(orderField))))
                    .asTable("page")
            )
            .where(DSL.noCondition());
    }

    @SneakyThrows
    public List<String> fragments(String query, String yaml) {
        List<String> split = Arrays.asList(StringUtils.split(yaml, "\n"));

        int first = IntStream.range(0, split.size())
            .filter(index -> StringUtils.indexOfIgnoreCase(split.get(index), query) >= 0)
            .findFirst()
            .orElse(0);

        int min = Math.max(0, first - 1);
        int max = Math.min(split.size(), min + 4);

        List<String> fragments = split
            .subList(min, max)
            .stream()
            .map(r ->
            {
                int i = StringUtils.indexOfIgnoreCase(r, query);

                if (i < 0) {
                    return r;
                } else {
                    return r.substring(0, i) + "[mark]" + r.substring(i, i + query.length()) + "[/mark]" + r.substring(i + query.length());
                }
            })
            .toList();

        return Collections.singletonList(String.join("\n", fragments));
    }

    public <R extends Record> SelectConditionStep<R> sort(SelectConditionStep<R> select, Pageable pageable) {
        if (pageable != null && pageable.getSort().isSorted()) {
            pageable
                .getSort()
                .getOrderBy()
                .forEach(order ->
                {
                    String column = camelToSnake(order.getProperty());
                    Field<Object> field = DSL.field(DSL.name(column));

                    select.orderBy(order.getDirection() == Sort.Order.Direction.ASC ? field.asc().nullsFirst() : field.desc().nullsLast());
                });
        }

        return select;
    }

    protected <R extends Record> Select<R> limit(SelectConditionStep<R> select, Pageable pageable) {
        if (pageable == null || pageable.getSize() == -1) {
            return select;
        }

        return select
            .limit(pageable.getSize())
            .offset(pageable.getOffset() - pageable.getSize());
    }

    protected <R extends Record> Select<R> pageable(SelectConditionStep<R> select, Pageable pageable) {
        select = this.sort(select, pageable);

        return this.limit(select, pageable);
    }

    public Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        return DSL.week(timestampField);
    }
}

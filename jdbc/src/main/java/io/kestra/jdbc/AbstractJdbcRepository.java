package io.kestra.jdbc;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.executions.metrics.MetricAggregation;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class AbstractJdbcRepository<T> {
    protected final QueueService queueService;

    protected final Class<T> cls;

    @Setter
    protected Function<Record, T> deserializer;

    @Getter
    protected final JooqDSLContextWrapper dslContextWrapper;

    @Getter
    protected Table<Record> table;

    public AbstractJdbcRepository(
        Class<T> cls,
        ApplicationContext applicationContext
    ) {
        this.cls = cls;
        this.queueService = applicationContext.getBean(QueueService.class);
        this.dslContextWrapper = applicationContext.getBean(JooqDSLContextWrapper.class);

        JdbcConfiguration jdbcConfiguration = applicationContext.getBean(JdbcConfiguration.class);

        this.table = DSL.table(jdbcConfiguration.tableConfig(cls).getTable());
    }

    abstract public Condition fullTextCondition(List<String> fields, String query);

    public String key(T entity) {
        String key = queueService.key(entity);

        if (key != null) {
            return key;
        }

        return IdUtils.create();
    }

    @SneakyThrows
    public Map<Field<Object>, Object> persistFields(T entity) {
        return new HashMap<>(ImmutableMap
            .of(io.kestra.jdbc.repository.AbstractJdbcRepository.field("value"), JdbcMapper.of().writeValueAsString(entity))
        );
    }

    public void persist(T entity) {
        this.persist(entity, null);
    }

    public void persist(T entity, Map<Field<Object>, Object> fields) {
        dslContextWrapper.transaction(configuration ->
            this.persist(entity, DSL.using(configuration), fields)
        );
    }

    public void persist(T entity, DSLContext dslContext, Map<Field<Object>, Object> fields) {
        Map<Field<Object>, Object> finalFields = fields == null ? this.persistFields(entity) : fields;

        dslContext
            .insertInto(table)
            .set(io.kestra.jdbc.repository.AbstractJdbcRepository.field("key"), key(entity))
            .set(finalFields)
            .onDuplicateKeyUpdate()
            .set(finalFields)
            .execute();
    }

    public int delete(T entity) {
        return dslContextWrapper.transactionResult(configuration -> {
            return this.delete(DSL.using(configuration), entity);
        });
    }

    public int delete(DSLContext dslContext, T entity) {
        DeleteConditionStep<Record> key = dslContext
            .delete(table)
            .where(io.kestra.jdbc.repository.AbstractJdbcRepository.field("key").eq(key(entity)));

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
        Integer hour = fields.contains("hour") ?  record.get("hour", Integer.class): null;
        Integer day = fields.contains("day") ?  record.get("day", Integer.class): null;
        Integer week = fields.contains("week") ?  record.get("week", Integer.class): null;
        Integer month = fields.contains("month") ?  record.get("month", Integer.class): null;
        Integer year = fields.contains("year") ?  record.get("year", Integer.class): null;

        switch (groupByType) {
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
            return JacksonMapper.ofJson().readValue(record, cls);
        } catch (InvalidTypeIdException e) {
            throw new DeserializationException(e);
        } catch (IOException e) {
            throw new DeserializationException(e);
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
            .map(r -> {
                int i = StringUtils.indexOfIgnoreCase(r, query);

                if (i < 0) {
                    return r;
                } else {
                    return r.substring(0, i) + "[mark]" + r.substring(i, i + query.length()) + "[/mark]" + r.substring(i + query.length());
                }
            })
            .collect(Collectors.toList());

        return Collections.singletonList(String.join("\n", fragments));
    }

    protected <R extends Record> SelectConditionStep<R> sort(SelectConditionStep<R> select, Pageable pageable) {
        if (pageable != null && pageable.getSort().isSorted()) {
            pageable
                .getSort()
                .getOrderBy()
                .forEach(order -> {
                    Field<Object> field = io.kestra.jdbc.repository.AbstractJdbcRepository.field(order.getProperty());

                    select.orderBy(order.getDirection() == Sort.Order.Direction.ASC ? field.asc() : field.desc());
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
}

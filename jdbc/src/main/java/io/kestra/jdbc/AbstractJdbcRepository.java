package io.kestra.jdbc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class AbstractJdbcRepository<T> {
    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    static {
        final SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
                jsonGenerator.writeString(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC)
                    .format(instant)
                );
            }
        });

        module.addSerializer(ZonedDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(ZonedDateTime instant, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
                jsonGenerator.writeString(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .format(instant)
                );
            }
        });

        MAPPER.registerModule(module);
    }

    protected final QueueService queueService;
    protected final Class<T> cls;

    @Getter
    protected final DSLContextWrapper dslContextWrapper;

    @Getter
    protected final Table<Record> table;

    public AbstractJdbcRepository(
        Class<T> cls,
        ApplicationContext applicationContext
    ) {
        this.cls = cls;
        this.queueService = applicationContext.getBean(QueueService.class);
        this.dslContextWrapper = applicationContext.getBean(DSLContextWrapper.class);

        JdbcConfiguration jdbcConfiguration = applicationContext.getBean(JdbcConfiguration.class);

        this.table = DSL.table(jdbcConfiguration.tableConfig(cls).getTable());
    }

    abstract public Condition fullTextCondition(List<String> fields, String query);

    protected String key(T entity) {
        String key = queueService.key(entity);

        if (key != null) {
            return key;
        }

        return IdUtils.create();
    }

    @SneakyThrows
    public Map<Field<Object>, Object> persistFields(T entity) {
        return new HashMap<>(ImmutableMap
            .of(AbstractRepository.field("value"), MAPPER.writeValueAsString(entity))
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
            .set(AbstractRepository.field("key"), key(entity))
            .set(finalFields)
            .onDuplicateKeyUpdate()
            .set(finalFields)
            .execute();
    }

    public void delete(T entity) {
        dslContextWrapper.transaction(configuration -> {
            this.delete(DSL.using(configuration), entity);
        });
    }

    public void delete(DSLContext dslContext, T entity) {
        dslContext
            .delete(table)
            .where(AbstractRepository.field("key").eq(key(entity)))
            .execute();
    }

    public <R extends Record> T map(R record) {
        try {
            return JacksonMapper.ofJson().readValue(record.get("value", String.class), cls);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public <R extends Record> Optional<T> fetchOne(Select<R> select) {
        return Optional.ofNullable(select.fetchAny())
            .map(this::map);
    }

    public <R extends Record> List<T> fetch(Select<R> select) {
        return select
            .fetch()
            .map(this::map);
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
        if (pageable.getSort().isSorted()) {
            pageable
                .getSort()
                .getOrderBy()
                .forEach(order -> {
                    Field<Object> field = AbstractRepository.field(order.getProperty());

                    select.orderBy(order.getDirection() == Sort.Order.Direction.ASC ? field.asc() : field.desc());
                });
        }

        return select;
    }

    protected <R extends Record> Select<R> limit(SelectConditionStep<R> select, Pageable pageable) {
       if (pageable.getSize() == -1) {
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

package io.kestra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class AbstractJdbcRepository<T extends DeletedInterface> {
    protected static final ObjectMapper mapper = JacksonMapper.ofJson();

    protected final QueueService queueService;
    protected final Class<T> cls;

    @Getter
    protected final DSLContext dslContext;

    @Getter
    protected final Table<Record> table;

    public AbstractJdbcRepository(
        Class<T> cls,
        ApplicationContext applicationContext
    ) {
        this.cls = cls;
        this.queueService = applicationContext.getBean(QueueService.class);
        this.dslContext = applicationContext.getBean(DSLContext.class);

        JdbcConfiguration jdbcConfiguration = applicationContext.getBean(JdbcConfiguration.class);

        this.table = DSL.table(jdbcConfiguration.tableConfig(cls).getTable());
    }

    @SneakyThrows
    public void persist(T entity) {
        String json = mapper.writeValueAsString(entity);

        InsertOnDuplicateSetMoreStep<Record> insert = dslContext.insertInto(table)
            .set(DSL.field(DSL.quotedName("key")), queueService.key(entity))
            .set(DSL.field("value"), json)
            .onDuplicateKeyUpdate()
            .set(DSL.field("value"), json);

        insert.execute();
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

    abstract public <R extends Record, E> ArrayListTotal<E> fetchPage(Select<R> select, RecordMapper<? super R, E> mapper);

    public <R extends Record> ArrayListTotal<T> fetchPage(Select<R> select) {
        return this.fetchPage(select, this::map);
    }

    @SneakyThrows
    public List<String> fragments(String query, String content) {
        String yaml = JacksonMapper.ofYaml().writeValueAsString(JacksonMapper.ofJson().readValue(content, this.cls));
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
                    return r.substring(0, i) + "<mark>" + r.substring(i, i + query.length()) + "</mark>" + r.substring(i + query.length());
                }
            })
            .collect(Collectors.toList());


        return Collections.singletonList(String.join("\n", fragments));
    }

    public <R extends Record> Select<R> pageable(SelectConditionStep<R> select, Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            pageable
                .getSort()
                .getOrderBy()
                .forEach(order -> {
                    Field<Object> field = DSL.field(order.getProperty());

                    select.orderBy(order.getDirection() == Sort.Order.Direction.ASC ? field.asc() : field.desc());
                });
        }

        return select.limit(pageable.getSize())
            .offset(pageable.getOffset() - pageable.getSize());
    }
}

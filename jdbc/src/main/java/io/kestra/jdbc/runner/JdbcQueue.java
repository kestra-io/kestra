package io.kestra.jdbc.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.QueueService;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.sql.DataSource;

@Slf4j
public abstract class JdbcQueue<T> implements QueueInterface<T> {
    protected static final ObjectMapper mapper = JacksonMapper.ofJson();

    private static ExecutorService poolExecutor;

    private final QueueService queueService;

    protected final Class<T> cls;

    protected final DSLContext dslContext;

    protected final DataSource dataSource;

    protected final Table<Record> table;

    protected final JdbcQueueIndexer jdbcQueueIndexer;

    protected Boolean isShutdown = false;

    public JdbcQueue(Class<T> cls, ApplicationContext applicationContext) {
        if (poolExecutor == null) {
            ExecutorsUtils executorsUtils = applicationContext.getBean(ExecutorsUtils.class);
            poolExecutor = executorsUtils.cachedThreadPool("jdbc-queue");
        }

        this.queueService = applicationContext.getBean(QueueService.class);
        this.cls = cls;
        this.dslContext = applicationContext.getBean(DSLContext.class);
        this.dataSource = applicationContext.getBean(DataSource.class);

        JdbcConfiguration jdbcConfiguration = applicationContext.getBean(JdbcConfiguration.class);

        this.table = DSL.table(jdbcConfiguration.tableConfig("queues").getTable());

        this.jdbcQueueIndexer = applicationContext.getBean(JdbcQueueIndexer.class);
    }

    @SneakyThrows
    protected Map<Field<Object>, Object> produceFields(String key, T message) {
        return new HashMap<>(ImmutableMap
            .of(
                DSL.field(DSL.quotedName("type")), this.cls.getName(),
                DSL.field(DSL.quotedName("key")), key != null ? key : IdUtils.create(),
                DSL.field(DSL.quotedName("value")), mapper.writeValueAsString(message)
            )
        );
    }

    private void produce(String key, T message) {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", this.cls.getName(), message);
        }

        dslContext.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            jdbcQueueIndexer.accept(context, message);

            context
                .insertInto(table)
                .set(this.produceFields(key, message))
                .execute();
        });
    }

    @Override
    public void emit(T message) {
        this.produce(queueService.key(message), message);
    }

    @Override
    public void delete(T message) throws QueueException {
        dslContext.transaction(configuration -> DSL
            .using(configuration)
            .delete(table)
            .where(DSL.field(DSL.quotedName("key")).eq(queueService.key(message)))
            .execute()
        );
    }

    public String consumerGroupName(Class<?> group) {
        return CaseFormat.UPPER_CAMEL.to(
            CaseFormat.LOWER_UNDERSCORE,
            group.getSimpleName()
        );
    }

    abstract protected Result<Record> receiveFetch(DSLContext ctx, Integer offset);

    abstract protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup);

    abstract protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, List<Integer> offsets);

    @Override
    public Runnable receive(Consumer<T> consumer) {
        AtomicInteger maxOffset = new AtomicInteger();

        // fetch max offset
        dslContext.transaction(configuration -> {
            DSLContext ctx = DSL.using(configuration);

            Integer integer = DSL
                .using(configuration)
                .select(DSL.max(DSL.field(DSL.quotedName("offset"))).as("max"))
                .from(table)
                .fetchAny("max", Integer.class);

            if (integer != null) {
                maxOffset.set(integer);
            }
        });

        return this.poll(() -> {
            dslContext.transaction(configuration -> {
                DSLContext ctx = DSL.using(configuration);

                Result<Record> fetch = this.receiveFetch(ctx, maxOffset.get());

                if (fetch.size() > 0) {
                    this.send(fetch, consumer);

                    List<Integer> offsets = fetch.map(record -> record.get("offset", Integer.class));

                    maxOffset.set(offsets.get(offsets.size() - 1));
                }
            });
        });
    }

    @Override
    public Runnable receive(Class<?> consumerGroup, Consumer<T> consumer) {
        String consumerGroupName = consumerGroupName(consumerGroup);

        return this.poll(() -> {
            dslContext.transaction(configuration -> {
                DSLContext ctx = DSL.using(configuration);

                Result<Record> fetch = this.receiveFetch(ctx, consumerGroupName);

                if (fetch.size() > 0) {
                    this.send(fetch, consumer);

                    this.updateGroupOffsets(
                        ctx,
                        consumerGroupName,
                        fetch.map(record -> record.get("offset", Integer.class))
                    );
                }

            });
        });
    }

    @SuppressWarnings("BusyWait")
    private Runnable poll(Runnable runnable) {
        AtomicBoolean running = new AtomicBoolean(true);

        poolExecutor.execute(() -> {
            while (running.get() && !this.isShutdown) {
                try {
                    runnable.run();
                } catch (CannotCreateTransactionException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Can't poll on receive", e);
                    }
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });


        return () -> {
            running.set(false);
        };
    }

    private void send(Result<Record> fetch, Consumer<T> consumer) {
        fetch
            .map(record -> {
                try {
                    return JacksonMapper.ofJson().readValue(record.get("value", String.class), cls);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            })
            .forEach(consumer);
    }

    @Override
    public void pause() {
        this.isShutdown = true;
    }

    @Override
    public void close() throws IOException {
        this.isShutdown = true;
        poolExecutor.shutdown();
    }
}

package io.kestra.jdbc.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.QueueService;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcConfiguration;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public abstract class JdbcQueue<T> implements QueueInterface<T> {
    protected static final ObjectMapper mapper = JacksonMapper.ofJson();

    private static ExecutorService poolExecutor;

    protected final QueueService queueService;

    protected final Class<T> cls;

    protected final JooqDSLContextWrapper dslContextWrapper;

    protected final DataSource dataSource;

    protected final Configuration configuration;

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
        this.dslContextWrapper = applicationContext.getBean(JooqDSLContextWrapper.class);
        this.dataSource = applicationContext.getBean(DataSource.class);
        this.configuration = applicationContext.getBean(Configuration.class);

        JdbcConfiguration jdbcConfiguration = applicationContext.getBean(JdbcConfiguration.class);

        this.table = DSL.table(jdbcConfiguration.tableConfig("queues").getTable());

        this.jdbcQueueIndexer = applicationContext.getBean(JdbcQueueIndexer.class);
    }

    @SneakyThrows
    protected Map<Field<Object>, Object> produceFields(String consumerGroup, String key, T message) {
        Map<Field<Object>, Object> fields = new HashMap<>();
        fields.put(AbstractJdbcRepository.field("type"), this.cls.getName());
        fields.put(AbstractJdbcRepository.field("key"), key != null ? key : IdUtils.create());
        fields.put(AbstractJdbcRepository.field("value"), JSONB.valueOf(mapper.writeValueAsString(message)));

        if (consumerGroup != null) {
            fields.put(AbstractJdbcRepository.field("consumer_group"), consumerGroup);
        }

        return fields;
    }

    private void produce(String consumerGroup, String key, T message, Boolean skipIndexer) {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", this.cls.getName(), message);
        }

        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            if (!skipIndexer) {
                jdbcQueueIndexer.accept(context, message);
            }

            context
                .insertInto(table)
                .set(this.produceFields(consumerGroup, key, message))
                .execute();
        });
    }

    public void emitOnly(String consumerGroup, T message) {
        this.produce(consumerGroup, queueService.key(message), message, true);
    }

    @Override
    public void emit(String consumerGroup, T message) {
        this.produce(consumerGroup, queueService.key(message), message, false);
    }

    @Override
    public void emitAsync(String consumerGroup, T message) throws QueueException {
        this.emit(consumerGroup, message);
    }

    @Override
    public void delete(String consumerGroup, T message) throws QueueException {
        dslContextWrapper.transaction(configuration -> DSL
            .using(configuration)
            .delete(table)
            .where(AbstractJdbcRepository.field("key").eq(queueService.key(message)))
            .execute()
        );
    }

    abstract protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, Integer offset);

    abstract protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, String queueType);

    abstract protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, String queueType, List<Integer> offsets);

    @Override
    public Runnable receive(String consumerGroup, Consumer<Either<T, DeserializationException>> consumer) {
        AtomicInteger maxOffset = new AtomicInteger();

        // fetch max offset
        dslContextWrapper.transaction(configuration -> {
            Integer integer = DSL
                .using(configuration)
                .select(DSL.max(AbstractJdbcRepository.field("offset")).as("max"))
                .from(table)
                .fetchAny("max", Integer.class);

            if (integer != null) {
                maxOffset.set(integer);
            }
        });

        return this.poll(() -> {
            Result<Record> fetch = dslContextWrapper.transactionResult(configuration -> {
                DSLContext ctx = DSL.using(configuration);

                Result<Record> result = this.receiveFetch(ctx, consumerGroup, maxOffset.get());

                if (!result.isEmpty()) {
                    List<Integer> offsets = result.map(record -> record.get("offset", Integer.class));

                    maxOffset.set(offsets.get(offsets.size() - 1));
                }

                return result;
            });

            this.send(fetch, consumer);

            return fetch.size();
        });
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer) {
        return this.receiveImpl(
            consumerGroup,
            queueType,
            (dslContext, eithers) -> {
                eithers.forEach(consumer);
            },
            false
        );
    }

    public Runnable receiveTransaction(String consumerGroup, Class<?> queueType, BiConsumer<DSLContext, List<Either<T, DeserializationException>>> consumer) {
        return this.receiveImpl(
            consumerGroup,
            queueType,
            consumer,
            true
        );
    }

    public Runnable receiveImpl(
        String consumerGroup,
        Class<?> queueType,
        BiConsumer<DSLContext, List<Either<T, DeserializationException>>> consumer,
        Boolean inTransaction
    ) {
        String queueName = queueName(queueType);

        return this.poll(() -> {
            Result<Record> fetch = dslContextWrapper.transactionResult(configuration -> {
                DSLContext ctx = DSL.using(configuration);

                Result<Record> result = this.receiveFetch(ctx, consumerGroup, queueName);

                if (!result.isEmpty()) {
                    if (inTransaction) {
                        consumer.accept(ctx, this.map(result));
                    }

                    this.updateGroupOffsets(
                        ctx,
                        consumerGroup,
                        queueName,
                        result.map(record -> record.get("offset", Integer.class))
                    );
                }

                return result;
            });

            if (!inTransaction) {
                consumer.accept(null, this.map(fetch));
            }

            return fetch.size();
        });
    }

    protected String queueName(Class<?> queueType) {
        return CaseFormat.UPPER_CAMEL.to(
            CaseFormat.LOWER_UNDERSCORE,
            queueType.getSimpleName()
        );
    }

    @SuppressWarnings("BusyWait")
    protected Runnable poll(Supplier<Integer> runnable) {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong sleep = new AtomicLong(configuration.getMaxPollInterval().toMillis());
        AtomicReference<ZonedDateTime> lastPoll = new AtomicReference<>(ZonedDateTime.now());

        poolExecutor.execute(() -> {
            while (running.get() && !this.isShutdown) {
                try {
                    Integer count = runnable.get();
                    if (count > 0) {
                        lastPoll.set(ZonedDateTime.now());
                    }

                    sleep.set(lastPoll.get().plus(configuration.getPollSwitchInterval()).compareTo(ZonedDateTime.now()) < 0 ?
                        configuration.getMaxPollInterval().toMillis() :
                        configuration.getMinPollInterval().toMillis()
                    );
                } catch (CannotCreateTransactionException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Can't poll on receive", e);
                    }
                }

                try {
                    Thread.sleep(sleep.get());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return () -> {
            running.set(false);
        };
    }

    protected List<Either<T, DeserializationException>> map(Result<Record> fetch) {
        return fetch
            .map(record -> {
                try {
                    return Either.left(JacksonMapper.ofJson().readValue(record.get("value", String.class), cls));
                } catch (JsonProcessingException e) {
                    return Either.right(new DeserializationException(e, record.get("value", String.class)));
                }
            });
    }

    protected void send(Result<Record> fetch, Consumer<Either<T, DeserializationException>> consumer) {
        this.map(fetch)
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

    @ConfigurationProperties("kestra.jdbc.queues")
    @Getter
    public static class Configuration {
        Duration minPollInterval = Duration.ofMillis(100);
        Duration maxPollInterval = Duration.ofMillis(500);
        Duration pollSwitchInterval = Duration.ofSeconds(30);
        Integer pollSize = 100;
    }
}

package io.kestra.jdbc.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.QueueService;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTableConfigs;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

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

import static io.kestra.core.utils.Rethrow.throwRunnable;

@Slf4j
public abstract class JdbcQueue<T> implements QueueInterface<T> {
    private static final int MAX_ASYNC_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    protected static final ObjectMapper MAPPER = JdbcMapper.of();

    private final ExecutorService poolExecutor;
    private final ExecutorService asyncPoolExecutor;

    protected final QueueService queueService;

    protected final Class<T> cls;

    protected final JooqDSLContextWrapper dslContextWrapper;

    protected final Configuration configuration;

    protected final MessageProtectionConfiguration messageProtectionConfiguration;

    private final MetricRegistry metricRegistry;

    protected final Table<Record> table;

    protected final JdbcQueueIndexer jdbcQueueIndexer;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    public JdbcQueue(Class<T> cls, ApplicationContext applicationContext) {
        ExecutorsUtils executorsUtils = applicationContext.getBean(ExecutorsUtils.class);
        this.poolExecutor = executorsUtils.cachedThreadPool("jdbc-queue-" + cls.getSimpleName());
        this.asyncPoolExecutor = executorsUtils.maxCachedThreadPool(MAX_ASYNC_THREADS, "jdbc-queue-async-" + cls.getSimpleName());

        this.queueService = applicationContext.getBean(QueueService.class);
        this.cls = cls;
        this.dslContextWrapper = applicationContext.getBean(JooqDSLContextWrapper.class);
        this.configuration = applicationContext.getBean(Configuration.class);
        this.messageProtectionConfiguration = applicationContext.getBean(MessageProtectionConfiguration.class);
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);

        JdbcTableConfigs jdbcTableConfigs = applicationContext.getBean(JdbcTableConfigs.class);

        this.table = DSL.table(jdbcTableConfigs.tableConfig("queues").table());

        this.jdbcQueueIndexer = applicationContext.getBean(JdbcQueueIndexer.class);
    }

    protected Map<Field<Object>, Object> produceFields(String consumerGroup, String key, T message) throws QueueException {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new QueueException("Unable to serialize the message", e);
        }

        if (messageProtectionConfiguration.enabled && bytes.length >= messageProtectionConfiguration.limit) {
            metricRegistry
                .counter(MetricRegistry.QUEUE_BIG_MESSAGE_COUNT, MetricRegistry.TAG_CLASS_NAME, cls.getName())
                .increment();

            // we let terminated execution messages to go through anyway
            if (!(message instanceof Execution execution) || !execution.getState().isTerminated()) {
                    throw new MessageTooBigException("Message of size " + bytes.length + " has exceeded the configured limit of " + messageProtectionConfiguration.limit);
            }
        }


        Map<Field<Object>, Object> fields = new HashMap<>();
        fields.put(AbstractJdbcRepository.field("type"), this.cls.getName());
        fields.put(AbstractJdbcRepository.field("key"), key != null ? key : IdUtils.create());
        fields.put(AbstractJdbcRepository.field("value"), JSONB.valueOf(new String(bytes)));

        if (consumerGroup != null) {
            fields.put(AbstractJdbcRepository.field("consumer_group"), consumerGroup);
        }

        return fields;
    }

    private void produce(String consumerGroup, String key, T message, Boolean skipIndexer) throws QueueException {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", this.cls.getName(), message);
        }

        Map<Field<Object>, Object> fields = this.produceFields(consumerGroup, key, message);

        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            if (!skipIndexer) {
                jdbcQueueIndexer.accept(context, message);
            }

            context
                .insertInto(table)
                .set(fields)
                .execute();
        });
    }

    public void emitOnly(String consumerGroup, T message) throws QueueException{
        this.produce(consumerGroup, queueService.key(message), message, true);
    }

    @Override
    public void emit(String consumerGroup, T message) throws QueueException {
        this.produce(consumerGroup, queueService.key(message), message, false);
    }

    @Override
    public void emitAsync(String consumerGroup, T message) throws QueueException {
        this.asyncPoolExecutor.submit(throwRunnable(() -> this.emit(consumerGroup, message)));
    }

    @Override
    public void delete(String consumerGroup, T message) throws QueueException {
        // Just do nothing!
        // The message will be removed by the indexer (synchronously if using the queue indexer, async otherwise),
        // and the queue has its own cleaner, which we better not mess with, as the 'queues' table is selected with a lock.
    }

    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, Integer offset) {
        return this.receiveFetch(ctx, consumerGroup, offset, true);
    }

    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, Integer offset, boolean forUpdate) {
        var select = ctx.select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            .from(this.table)
            .where(buildTypeCondition(this.cls.getName()));

        if (offset != 0) {
            select = select.and(AbstractJdbcRepository.field("offset").gt(offset));
        }

        if (consumerGroup != null) {
            select = select.and(AbstractJdbcRepository.field("consumer_group").eq(consumerGroup));
        } else {
            select = select.and(AbstractJdbcRepository.field("consumer_group").isNull());
        }

        var limitSelect = select
            .orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(configuration.getPollSize());
        ResultQuery<Record2<Object, Object>> configuredSelect = limitSelect;

        if (forUpdate) {
            configuredSelect = limitSelect.forUpdate().skipLocked();
        }

        return configuredSelect
            .fetchMany()
            .getFirst();
    }

    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, String queueType) {
        return this.receiveFetch(ctx, consumerGroup, queueType, true);
    }

    abstract protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, String queueType, boolean forUpdate);

    abstract protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, String queueType, List<Integer> offsets);

    protected abstract Condition buildTypeCondition(String type);

    @Override
    public Runnable receive(String consumerGroup, Consumer<Either<T, DeserializationException>> consumer, boolean forUpdate) {
        AtomicInteger maxOffset = new AtomicInteger();

        // fetch max offset
        dslContextWrapper.transaction(configuration -> {
            var select = DSL
                .using(configuration)
                .select(DSL.max(AbstractJdbcRepository.field("offset")).as("max"))
                .from(table)
                .where(buildTypeCondition(this.cls.getName()));
            if (consumerGroup != null) {
                select = select.and(AbstractJdbcRepository.field("consumer_group").eq(consumerGroup));
            } else {
                select = select.and(AbstractJdbcRepository.field("consumer_group").isNull());
            }

            Integer integer = select.fetchAny("max", Integer.class);
            if (integer != null) {
                maxOffset.set(integer);
            }
        });

        return this.poll(() -> {
            Result<Record> fetch = dslContextWrapper.transactionResult(configuration -> {
                DSLContext ctx = DSL.using(configuration);

                Result<Record> result = this.receiveFetch(ctx, consumerGroup, maxOffset.get(), forUpdate);

                if (!result.isEmpty()) {
                    List<Integer> offsets = result.map(record -> record.get("offset", Integer.class));

                    maxOffset.set(offsets.getLast());
                }

                return result;
            });

            this.send(fetch, consumer);

            return fetch.size();
        });
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer, boolean forUpdate) {
        return this.receiveImpl(
            consumerGroup,
            queueType,
            (dslContext, eithers) -> {
                eithers.forEach(consumer);
            },
            false,
            forUpdate
        );
    }

    public Runnable receiveBatch(Class<?> queueType, Consumer<List<Either<T, DeserializationException>>> consumer) {
        return receiveBatch(null, queueType, consumer);
    }

    public Runnable receiveBatch(String consumerGroup, Class<?> queueType, Consumer<List<Either<T, DeserializationException>>> consumer) {
        return receiveBatch(consumerGroup, queueType, consumer, true);
    }

    public Runnable receiveBatch(String consumerGroup, Class<?> queueType, Consumer<List<Either<T, DeserializationException>>> consumer, boolean forUpdate) {
        return this.receiveImpl(
            consumerGroup,
            queueType,
            (dslContext, eithers) -> {
                consumer.accept(eithers);
            },
            false,
            forUpdate
        );
    }

    public Runnable receiveTransaction(String consumerGroup, Class<?> queueType, BiConsumer<DSLContext, List<Either<T, DeserializationException>>> consumer) {
        return this.receiveImpl(
            consumerGroup,
            queueType,
            consumer,
            true,
            true
        );
    }

    public Runnable receiveImpl(
        String consumerGroup,
        Class<?> queueType,
        BiConsumer<DSLContext, List<Either<T, DeserializationException>>> consumer,
        Boolean inTransaction,
        boolean forUpdate
    ) {
        String queueName = queueName(queueType);

        return this.poll(() -> {
            Result<Record> fetch = dslContextWrapper.transactionResult(configuration -> {
                DSLContext ctx = DSL.using(configuration);

                Result<Record> result = this.receiveFetch(ctx, consumerGroup, queueName, forUpdate);

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
            while (running.get() && !this.isClosed.get()) {
                if (!this.isPaused.get()) {
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
                }

                try {
                    Thread.sleep(sleep.get());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return () -> running.set(false);
    }

    protected List<Either<T, DeserializationException>> map(Result<Record> fetch) {
        return fetch
            .map(record -> {
                try {
                    return Either.left(MAPPER.readValue(record.get("value", String.class), cls));
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
        this.isPaused.set(true);
    }

    @Override
    public void resume() {
        this.isPaused.set(false);
    }

    @Override
    public void close() throws IOException {
        if (!this.isClosed.compareAndSet(false, true)) {
            return;
        }
        this.poolExecutor.shutdown();
        this.asyncPoolExecutor.shutdown();
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

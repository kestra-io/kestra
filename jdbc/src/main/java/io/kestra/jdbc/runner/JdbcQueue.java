package io.kestra.jdbc.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Iterables;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.*;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.JdbcTableConfigs;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Value;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.exception.DataException;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwRunnable;

@Slf4j
public abstract class JdbcQueue<T> implements QueueInterface<T> {
    private static final int MAX_ASYNC_THREADS = Runtime.getRuntime().availableProcessors();
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

    private final boolean immediateRepoll;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    private final Counter bigMessageCounter;

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

        this.immediateRepoll = applicationContext.getProperty("kestra.jdbc.queues.immediate-repoll", Boolean.class).orElse(true);

        // init metrics we can at post construct to avoid costly Metric.Id computation
        this.bigMessageCounter = metricRegistry
            .counter(MetricRegistry.METRIC_QUEUE_BIG_MESSAGE_COUNT, MetricRegistry.METRIC_QUEUE_BIG_MESSAGE_COUNT_DESCRIPTION, MetricRegistry.TAG_CLASS_NAME, queueType());
    }

    protected Map<Field<Object>, Object> produceFields(String consumerGroup, String key, T message) throws QueueException {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new QueueException("Unable to serialize the message", e);
        }

        if (messageProtectionConfiguration.enabled && bytes.length >= messageProtectionConfiguration.limit) {
            this.bigMessageCounter.increment();

            // we let terminated execution messages to go through anyway
            if (!(message instanceof Execution execution) || !execution.getState().isTerminated()) {
                    throw new MessageTooBigException("Message of size " + bytes.length + " has exceeded the configured limit of " + messageProtectionConfiguration.limit);
            }
        }


        Map<Field<Object>, Object> fields = new HashMap<>();
        fields.put(AbstractJdbcRepository.field("type"), queueType());
        fields.put(AbstractJdbcRepository.field("key"), key != null ? key : IdUtils.create());
        fields.put(AbstractJdbcRepository.field("value"), JSONB.valueOf(new String(bytes)));

        if (consumerGroup != null) {
            fields.put(AbstractJdbcRepository.field("consumer_group"), consumerGroup);
        }

        return fields;
    }

    private void produce(String consumerGroup, String key, T message, Boolean skipIndexer) throws QueueException {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", queueType(), message);
        }

        Map<Field<Object>, Object> fields = this.produceFields(consumerGroup, key, message);

        try {
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
        } catch (DataException e) { // The exception is from the data itself, not the database/network/driver so instead of fail fast, we throw a recoverable QueueException
            // Postgres refuses to store JSONB with the '\0000' codepoint as it has no textual representation.
            // We try to detect that and fail with a specific exception so the Worker can recover from it.
            if (e.getMessage() != null && e.getMessage().contains("ERROR: unsupported Unicode escape sequence")) {
                throw new UnsupportedMessageException(e.getMessage(), e);
            }
            throw new QueueException("Unable to emit a message to the queue", e);
        }

        String[] tags = consumerGroup == null ? new String [] { MetricRegistry.TAG_QUEUE_TYPE, queueType() } :
            new String [] { MetricRegistry.TAG_QUEUE_TYPE, queueType(), MetricRegistry.TAG_QUEUE_CONSUMER_GROUP, consumerGroup };
        metricRegistry
            .counter(MetricRegistry.METRIC_QUEUE_PRODUCE_COUNT, MetricRegistry.METRIC_QUEUE_PRODUCE_COUNT_DESCRIPTION, tags)
            .increment();
    }

    public void emitOnly(String consumerGroup, T message) throws QueueException{
        this.produce(consumerGroup, queueService.key(message), message, true);
    }

    @Override
    public void emit(String consumerGroup, T message) throws QueueException {
        this.produce(consumerGroup, queueService.key(message), message, false);
    }

    @Override
    public void emitAsync(String consumerGroup, List<T> messages) throws QueueException {
        this.asyncPoolExecutor.submit(throwRunnable(() -> messages.forEach(throwConsumer(message -> this.emit(consumerGroup, message)))));
    }

    @Override
    public void delete(String consumerGroup, T message) throws QueueException {
        // Just do nothing!
        // The message will be removed by the indexer (synchronously if using the queue indexer, async otherwise),
        // and the queue has its own cleaner, which we better not mess with, as the 'queues' table is selected with a lock.
    }

    /**
     * Delete all messages of the queue for this key.
     * This is used to purge a queue for a specific key.
     */
    public void deleteByKey(String key) throws QueueException {
        dslContextWrapper.transaction(configuration -> {
            int deleted = DSL
                .using(configuration)
                .delete(this.table)
                .where(buildTypeCondition(queueType()))
                .and(AbstractJdbcRepository.field("key").eq(key))
                .execute();
            log.debug("Cleaned {} records for key {}", deleted, key);
        });
    }

    protected String queueType() {
        return this.cls.getName();
    }

    /**
     * Delete all messages of the queue for a set of keys.
     * This is used to purge a queue for specific keys.
     */
    public void deleteByKeys(List<String> keys) throws QueueException {
        // process in batches of 100 items to avoid too big IN clausecQueue
        Iterables.partition(keys, 100).forEach(batch -> {
            dslContextWrapper.transaction(configuration -> {
                int deleted = DSL
                    .using(configuration)
                    .delete(this.table)
                    .where(buildTypeCondition(queueType()))
                    .and(AbstractJdbcRepository.field("key").in(batch))
                    .execute();
                log.debug("Cleaned {} records for keys {}", deleted, batch);
            });
        });
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
            .where(buildTypeCondition(queueType()));

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

    protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, String queueType, List<Integer> offsets) {
        if (!ListUtils.isEmpty(offsets)) {
            doUpdateGroupOffsets(ctx, consumerGroup, queueType, offsets);
        }
    }

    abstract protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, String queueType, boolean forUpdate);

    abstract protected void doUpdateGroupOffsets(DSLContext ctx, String consumerGroup, String queueType, List<Integer> offsets);

    protected abstract Condition buildTypeCondition(String type);

    @Override
    public Runnable receive(String consumerGroup, Consumer<Either<T, DeserializationException>> consumer, boolean forUpdate) {
        String[] tags = consumerGroup == null ? new String [] { MetricRegistry.TAG_QUEUE_TYPE, queueType() } :
            new String [] { MetricRegistry.TAG_QUEUE_TYPE, queueType(), MetricRegistry.TAG_QUEUE_CONSUMER_GROUP, consumerGroup };
        AtomicInteger pollSize = new AtomicInteger();
        this.metricRegistry
            .gauge(MetricRegistry.METRIC_QUEUE_POLL_SIZE, MetricRegistry.METRIC_QUEUE_POLL_SIZE_DESCRIPTION, pollSize, tags);

        AtomicInteger maxOffset = new AtomicInteger();

        // fetch max offset
        dslContextWrapper.transaction(configuration -> {
            var select = DSL
                .using(configuration)
                .select(DSL.max(AbstractJdbcRepository.field("offset")).as("max"))
                .from(table)
                .where(buildTypeCondition(queueType()));
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

        Timer timer = this.metricRegistry
            .timer(MetricRegistry.METRIC_QUEUE_RECEIVE_DURATION, MetricRegistry.METRIC_QUEUE_RECEIVE_DURATION_DESCRIPTION, tags);
        return this.poll(() -> timer.record(() -> {
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

            pollSize.set(fetch.size());
            return fetch.size();
        }));
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
        String[] tags = consumerGroup == null ? new String [] { MetricRegistry.TAG_QUEUE_TYPE, queueType(), MetricRegistry.TAG_QUEUE_CONSUMER, queueName } :
            new String [] { MetricRegistry.TAG_QUEUE_TYPE, queueType(), MetricRegistry.TAG_QUEUE_CONSUMER, queueName, MetricRegistry.TAG_QUEUE_CONSUMER_GROUP, consumerGroup };
        AtomicInteger pollSize = new AtomicInteger();
        this.metricRegistry
            .gauge(MetricRegistry.METRIC_QUEUE_POLL_SIZE, MetricRegistry.METRIC_QUEUE_POLL_SIZE_DESCRIPTION, pollSize, tags);

        Timer timer = this.metricRegistry
            .timer(MetricRegistry.METRIC_QUEUE_RECEIVE_DURATION, MetricRegistry.METRIC_QUEUE_RECEIVE_DURATION_DESCRIPTION, tags);
        return this.poll(() -> timer.record(() -> {
            Result<Record> fetch = dslContextWrapper.transactionResult(configuration -> {
                DSLContext ctx = DSL.using(configuration);

                Result<Record> result = this.receiveFetch(ctx, consumerGroup, queueName, forUpdate);

                if (!result.isEmpty() && inTransaction) {
                    consumer.accept(ctx, this.map(result));
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
                dslContextWrapper.transaction(configuration ->
                    this.updateGroupOffsets(
                        DSL.using(configuration),
                        consumerGroup,
                        queueName,
                        fetch.map(record -> record.get("offset", Integer.class))
                    ));
            }

            pollSize.set(fetch.size());
            return fetch.size();
        }));
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

        poolExecutor.execute(() -> {
            List<Configuration.Step> steps = configuration.computeSteps();
            Duration sleep = configuration.minPollInterval;
            ZonedDateTime lastPoll = ZonedDateTime.now();
            while (running.get() && !this.isClosed.get()) {
                if (!this.isPaused.get()) {
                    try {
                        Integer count = runnable.get();
                        if (count > 0) {
                            lastPoll = ZonedDateTime.now();
                            sleep = configuration.minPollInterval;
                            if (immediateRepoll) {
                                continue;
                            } else if (count.equals(configuration.pollSize)) {
                                // Note: this provides better latency on high throughput: when Kestra is a top capacity,
                                // it will not do a sleep and immediately poll again.
                                // We can even have better latency at even higher latency by continuing for positive count,
                                // but at higher database cost.
                                // Current impl balance database cost with latency.
                                continue;
                            }
                        } else {
                            ZonedDateTime finalLastPoll = lastPoll;
                            // get all poll steps which duration is less than the duration between last poll and now
                            List<Configuration.Step> selectedSteps = steps.stream()
                                .takeWhile(step -> finalLastPoll.plus(step.switchInterval()).compareTo(ZonedDateTime.now()) < 0)
                                .toList();
                            // then select the last one (longest) or minPoll if all are beyond while means we are under the first interval
                            sleep = selectedSteps.isEmpty() ? configuration.minPollInterval : selectedSteps.getLast().pollInterval();
                        }
                    } catch (CannotCreateTransactionException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Can't poll on receive", e);
                        }
                    }
                }

                try {
                    Thread.sleep(sleep);
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
        Duration minPollInterval = Duration.ofMillis(25);
        Duration maxPollInterval = Duration.ofMillis(500);
        Duration pollSwitchInterval = Duration.ofSeconds(60);
        Integer pollSize = 100;
        Integer switchSteps = 5;

        public List<Step> computeSteps() {
            if (this.maxPollInterval.compareTo(this.minPollInterval) <= 0) {
                throw new IllegalArgumentException("'maxPollInterval' (" + this.maxPollInterval + ") must be greater than 'minPollInterval' (" + this.minPollInterval + ")");
            }

            List<Step> steps = new ArrayList<>();
            Step currentStep = new Step(this.maxPollInterval, this.pollSwitchInterval);
            steps.add(currentStep);
            for (int i = 0; i < switchSteps; i++) {
                Duration stepPollInterval = Duration.ofMillis(currentStep.pollInterval().toMillis() / 2);
                if (stepPollInterval.compareTo(minPollInterval) < 0) {
                    stepPollInterval = minPollInterval;
                }
                Duration stepSwitchInterval = Duration.ofMillis(currentStep.switchInterval().toMillis() / 2);
                currentStep = new Step(stepPollInterval, stepSwitchInterval);
                steps.add(currentStep);
            }
            Collections.sort(steps);
            return steps;
        }

        public record Step (Duration pollInterval, Duration switchInterval) implements Comparable<Step> {
            @Override
            public int compareTo(Step o) {
                return this.switchInterval.compareTo(o.switchInterval);
            }
        }
    }
}

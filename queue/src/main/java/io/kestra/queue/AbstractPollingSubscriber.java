package io.kestra.queue;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.Either;
import io.kestra.queue.poller.QueuePoller;
import io.kestra.queue.poller.QueuePollerConfiguration;

import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;

/**
 * A subscriber that uses a polling mechanism.
 * It used the {@link QueuePoller} to periodically execute a poll query (whether on a database or a message broker)
 * with a polling sleep defined by a {@link QueuePollerConfiguration}.
 */
@Slf4j
public abstract class AbstractPollingSubscriber<T extends Event> extends AbstractSubscriber<T> {
    private final QueuePollerConfiguration configuration;

    public AbstractPollingSubscriber(Class<T> cls, String queueName, QueueService queueService, MetricRegistry metricRegistry, IgnoreExecutionService ignoreExecutionService,
        QueuePollerConfiguration configuration) {
        super(cls, queueName, queueService, metricRegistry, ignoreExecutionService);

        this.configuration = configuration;
    }

    @Override
    public QueueSubscriber<T> subscribe(Consumer<Either<T, DeserializationException>> consumer) {
        QueuePoller queuePoller = new QueuePoller(configuration, () -> this.poll(message -> processMessage(message, consumer)));

        return internalSubscribe(queuePoller);
    }

    @Override
    public QueueSubscriber<T> subscribeBatch(Consumer<List<Either<T, DeserializationException>>> consumer) {
        QueuePoller queuePoller = new QueuePoller(configuration, () -> this.pollBatch(message -> processBatchMessages(message, consumer)));

        return internalSubscribe(queuePoller);
    }

    /**
     * Poll a single message from the database or the message broker and consume it.
     */
    protected abstract Integer poll(Consumer<byte[]> messageConsumer);

    /**
     * Poll a batch of messages from the database or the message broker and consume it.
     */
    protected abstract Integer pollBatch(Consumer<List<byte[]>> messageConsumer);

    private QueueSubscriber<T> internalSubscribe(QueuePoller queuePoller) {
        this.queueService.execute(() ->
        {
            List<QueuePollerConfiguration.Step> steps = configuration.computeSteps();
            ZonedDateTime lastPoll = ZonedDateTime.now();

            try {
                while (this.isActive()) {
                    try {
                        this.waitIfPaused();

                        // Check if the loop was stopped while being paused
                        if (!this.isActive()) {
                            return;
                        }

                        lastPoll = queuePoller.pollOnce(lastPoll, steps);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Interrupted while waiting. Stopping.", e);
                        this.markEnd();
                    } catch (Exception e) {
                        if (
                            "io.micronaut.transaction.exceptions.CannotCreateTransactionException".equals(e.getClass().getName())
                                || "io.micronaut.data.connection.jdbc.exceptions.CannotGetJdbcConnectionException".equals(e.getClass().getName())
                        ) {
                            // we ignore transaction/connection errors as they occur when the datasource is closed during shutdown
                            log.debug("Can't poll on receive", e);
                        } else {
                            this.markEnd(e);
                        }
                    }
                }
            } finally {
                this.markEnd();
            }
        });

        await().until(this::isActive);

        return this;
    }
}

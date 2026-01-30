package io.kestra.queue.jdbc.client;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.runner.JdbcQueueConfiguration;
import io.kestra.queue.AbstractSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.queue.QueueService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public abstract class JdbcSubscriber<T extends Event> extends AbstractSubscriber<T> {
    protected final JdbcQueueClient jdbcQueueClient;
    protected final String queueName;

    public JdbcSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName
    ) {
        super(cls, queueService);

        this.jdbcQueueClient = jdbcQueueClient;
        this.queueName = queueName;
    }

    protected abstract Integer poll(JdbcQueueClient.MessageConsumer<String, Exception> messageConsumer);

    protected abstract Integer pollBatch(JdbcQueueClient.MessageConsumer<List<String>, Exception> messageConsumer);

    protected abstract void init();

    @Override
    public QueueSubscriber<T> subscribe(Consumer<Either<T, DeserializationException>> consumer) {
        this.queueService.execute(() -> {
            List<JdbcQueueConfiguration.Step> steps = this.jdbcQueueClient.getConfiguration().computeSteps();
            ZonedDateTime lastPoll = ZonedDateTime.now();
            Duration sleepDuration;

            this.init();

            while (this.isRunning() || this.isPaused()) {
                try {
                    this.waitIfPaused();

                    Integer count = this.poll(message -> {
                        try {
                            Either<T, DeserializationException> event = this.queueService.deserialize(this.cls, message);
                            consumer.accept(event);

                            return null;
                        } catch (Exception e) {
                            log.warn(
                                "[{}] messages failed and was resubmitted to active queue: {}",
                                cls.getSimpleName(),
                                message,
                                e
                            );

                            return e;
                        }
                    });

                    // define sleep time before next poll, could be immediate if we have messages to process
                    if (count > 0) {
                        lastPoll = ZonedDateTime.now();
                        sleepDuration = this.jdbcQueueClient.getConfiguration().minPollInterval();

                        if (this.jdbcQueueClient.getConfiguration().immediateRepoll()) {
                            sleepDuration = Duration.ofSeconds(0);
                        } else if (count.equals(jdbcQueueClient.getConfiguration().pollSize())) {
                            // Note: this provides better latency on high throughput: when Kestra is a top capacity,
                            // it will not do a sleep and immediately poll again.
                            // We can even have better latency at even higher latency by continuing for positive count,
                            // but at higher database cost.
                            // Current impl balance database cost with latency.
                            sleepDuration = Duration.ofSeconds(0);
                        }
                    } else {
                        ZonedDateTime finalLastPoll = lastPoll;
                        // get all poll steps which duration is less than the duration between last poll and now
                        List<JdbcQueueConfiguration.Step> selectedSteps = steps.stream()
                            .takeWhile(step -> finalLastPoll.plus(step.switchInterval()).compareTo(ZonedDateTime.now()) < 0)
                            .toList();
                        // then select the last one (longest) or minPoll if all are beyond while means we are under the first interval
                        sleepDuration = selectedSteps.isEmpty() ? jdbcQueueClient.getConfiguration().minPollInterval() : selectedSteps.getLast().pollInterval();
                    }

                    // keep the thread running
                    Thread.sleep(sleepDuration);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting. Stopping.", e);
                    this.markEnd();
                } catch (Exception e) {
                    log.error("Unexpected error while polling messages", e);
                    this.markEnd();
                }
            }

            this.markEnd();
        });

        Await.until(this::isRunning);

        return this;
    }

    @Override
    public QueueSubscriber<T> subscribeBatch(Consumer<List<Either<T, DeserializationException>>> consumer) {
        this.queueService.execute(() -> {
            List<JdbcQueueConfiguration.Step> steps = this.jdbcQueueClient.getConfiguration().computeSteps();
            ZonedDateTime lastPoll = ZonedDateTime.now();
            Duration sleepDuration;

            this.init();

            while (this.isRunning() || this.isPaused()) {
                try {
                    this.waitIfPaused();

                    Integer count = this.pollBatch(messages -> {
                        try {
                            List<Either<T, DeserializationException>> events = messages.stream().map(message -> this.queueService.deserialize(this.cls, message)).toList();
                            consumer.accept(events);

                            return null;
                        } catch (Exception e) {
                            log.warn(
                                "[{}] messages failed and was resubmitted to active queue: {}",
                                cls.getSimpleName(),
                                messages.size(),
                                e
                            );

                            return e;
                        }
                    });

                    // define sleep time before next poll, could be immediate if we have messages to process
                    if (count > 0) {
                        lastPoll = ZonedDateTime.now();
                        sleepDuration = this.jdbcQueueClient.getConfiguration().minPollInterval();

                        if (this.jdbcQueueClient.getConfiguration().immediateRepoll()) {
                            sleepDuration = Duration.ofSeconds(0);
                        } else if (count.equals(jdbcQueueClient.getConfiguration().pollSize())) {
                            // Note: this provides better latency on high throughput: when Kestra is a top capacity,
                            // it will not do a sleep and immediately poll again.
                            // We can even have better latency at even higher latency by continuing for positive count,
                            // but at higher database cost.
                            // Current impl balance database cost with latency.
                            sleepDuration = Duration.ofSeconds(0);
                        }
                    } else {
                        ZonedDateTime finalLastPoll = lastPoll;
                        // get all poll steps which duration is less than the duration between last poll and now
                        List<JdbcQueueConfiguration.Step> selectedSteps = steps.stream()
                            .takeWhile(step -> finalLastPoll.plus(step.switchInterval()).compareTo(ZonedDateTime.now()) < 0)
                            .toList();
                        // then select the last one (longest) or minPoll if all are beyond while means we are under the first interval
                        sleepDuration = selectedSteps.isEmpty() ? jdbcQueueClient.getConfiguration().minPollInterval() : selectedSteps.getLast().pollInterval();
                    }

                    // keep the thread running
                    Thread.sleep(sleepDuration);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting. Stopping.", e);
                    this.markEnd();
                } catch (Exception e) {
                    log.error("Unexpected error while polling messages", e);
                    this.markEnd();
                }
            }

            this.markEnd();
        });

        Await.until(this::isRunning);

        return this;
    }
}

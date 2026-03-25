package io.kestra.queue.poller;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Class responsible for continuously executing a polling query.
 *
 * @see QueuePollerConfiguration for configuration details explaining the polling mechanism.
 */
public class QueuePoller {
    private final QueuePollerConfiguration configuration;
    private final Callable<Integer> pollingQuery;

    /**
     * Creates a new {@link QueuePoller} instance.
     *
     * @param configuration the {@link QueuePollerConfiguration}.
     * @param pollingQuery the query to be executed.
     */
    public QueuePoller(final QueuePollerConfiguration configuration,
        final Callable<Integer> pollingQuery) {
        this.configuration = Objects.requireNonNull(configuration);
        this.pollingQuery = Objects.requireNonNull(pollingQuery);
    }

    /**
     * Poll once: execute the polling query once and return the next poll date
     *
     * @throws Exception if the pollQuery Callable throws an exception
     * @throws InterruptedException if the poll sleep is interrupted
     */
    public ZonedDateTime pollOnce(ZonedDateTime lastPoll, List<QueuePollerConfiguration.Step> steps) throws Exception {
        Duration sleep;
        Integer count = pollingQuery.call();
        if (count > 0) {
            lastPoll = ZonedDateTime.now();
            sleep = configuration.minPollInterval();
            if (configuration.immediateRepoll()) {
                return lastPoll;
            } else if (count.equals(configuration.pollSize())) {
                // Note: this provides better latency on high throughput: when Kestra is a top capacity,
                // it will not do a sleep and immediately poll again.
                // We can even have better latency at even higher latency by continuing for positive count,
                // but at higher database cost.
                // Current impl balance database cost with latency.
                return lastPoll;
            }
        } else {
            ZonedDateTime finalLastPoll = lastPoll;
            // get all poll steps whose duration is less than the duration between the last poll and now
            List<QueuePollerConfiguration.Step> selectedSteps = steps.stream()
                .takeWhile(step -> finalLastPoll.plus(step.switchInterval()).compareTo(ZonedDateTime.now()) < 0)
                .toList();
            // then select the last one (longest) or minPoll if all are beyond while means we are under the first interval
            sleep = selectedSteps.isEmpty() ? configuration.minPollInterval() : selectedSteps.getLast().pollInterval();
        }

        Thread.sleep(sleep);

        return lastPoll;
    }
}

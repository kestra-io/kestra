package io.kestra.queue.poller;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePollerTest {

    @Test
    void shouldReturnPreviousDateWhenNothingPolled() throws Exception {
        var config = new QueuePollerConfiguration(
            Duration.ofMillis(10),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            10,
            5,
            true
        );
        var queuePoller = new QueuePoller(config, () ->
        {
            Thread.sleep(1); //  make sure the returned date is more than now
            return 0;
        });
        var now = ZonedDateTime.now();

        var nextPoll = queuePoller.pollOnce(now, config.computeSteps());

        assertThat(nextPoll).isEqualTo(now);
    }

    @Test
    void shouldReturnNowWhenSomethingPolled() throws Exception {
        var config = new QueuePollerConfiguration(
            Duration.ofMillis(10),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            10,
            5,
            true
        );
        var queuePoller = new QueuePoller(config, () ->
        {
            Thread.sleep(1); //  make sure the returned date is more than now
            return 1;
        });
        var now = ZonedDateTime.now();

        var nextPoll = queuePoller.pollOnce(now, config.computeSteps());

        assertThat(nextPoll).isAfter(now);
    }
}
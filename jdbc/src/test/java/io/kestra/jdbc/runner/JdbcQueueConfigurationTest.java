package io.kestra.jdbc.runner;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JdbcQueueConfigurationTest {
    @Test
    void shouldFailWhenMaxPollLessThanMinPoll() {
        var configuration = new JdbcQueueConfiguration(
            Duration.ofSeconds(2),
            Duration.ofSeconds(1),
            Duration.ofSeconds(60),
            100,
            5,
            true
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> configuration.computeSteps());
        assertThat(exception.getMessage()).isEqualTo("'maxPollInterval' (PT1S) must be greater than or equal to 'minPollInterval' (PT2S)");
    }

    @Test
    void shouldCompute5StepsByDefault() {
        var configuration = new JdbcQueueConfiguration(
            Duration.ofMillis(25),
            Duration.ofMillis(500),
            Duration.ofSeconds(60),
            100,
            5,
            true
        );

        // By default, we have 5 computed steps + the minPoll
        List<JdbcQueueConfiguration.Step> steps = configuration.computeSteps();
        assertThat(steps.size()).isEqualTo(6);
        assertThat(steps).contains(
            new JdbcQueueConfiguration.Step(Duration.ofMillis(25), Duration.ofMillis(1875)),
            new JdbcQueueConfiguration.Step(Duration.ofMillis(31), Duration.ofMillis(3750)),
            new JdbcQueueConfiguration.Step(Duration.ofMillis(62), Duration.ofMillis(7500)),
            new JdbcQueueConfiguration.Step(Duration.ofMillis(125), Duration.ofSeconds(15)),
            new JdbcQueueConfiguration.Step(Duration.ofMillis(250), Duration.ofSeconds(30)),
            new JdbcQueueConfiguration.Step(Duration.ofMillis(500), Duration.ofSeconds(60))
        );
    }

    @Test
    void shouldCompute6Steps() {
        var configuration = new JdbcQueueConfiguration(
            Duration.ofSeconds(1),
            Duration.ofSeconds(60),
            Duration.ofSeconds(60),
            100,
            6,
            true
        );
        // As configured, we should have 6 steps + the minPoll
        List<JdbcQueueConfiguration.Step> steps = configuration.computeSteps();
        assertThat(steps.size()).isEqualTo(7);
        assertThat(steps).contains(
            new JdbcQueueConfiguration.Step(Duration.ofMillis(1000), Duration.ofMillis(937)),
            new JdbcQueueConfiguration.Step(Duration.ofMillis(1875), Duration.ofMillis(1875)),
            new JdbcQueueConfiguration.Step(Duration.ofMillis(3750), Duration.ofMillis(3750)),
            new JdbcQueueConfiguration.Step(Duration.ofMillis(7500), Duration.ofMillis(7500)),
            new JdbcQueueConfiguration.Step(Duration.ofSeconds(15), Duration.ofSeconds(15)),
            new JdbcQueueConfiguration.Step(Duration.ofSeconds(30), Duration.ofSeconds(30)),
            new JdbcQueueConfiguration.Step(Duration.ofSeconds(60), Duration.ofSeconds(60))
        );
    }

    @Test
    void shouldComputeSingleStepWhenMinEqualsMax() {
        var configuration = new JdbcQueueConfiguration(
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(60),
            100,
            5,
            true
        );
        List<JdbcQueueConfiguration.Step> steps = configuration.computeSteps();
        assertThat(steps.size()).isEqualTo(1);
        assertThat(steps).contains(
            new JdbcQueueConfiguration.Step(Duration.ofSeconds(1), Duration.ZERO)
        );
    }
}

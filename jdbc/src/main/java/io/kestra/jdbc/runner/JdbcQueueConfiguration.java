package io.kestra.jdbc.runner;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ConfigurationProperties("kestra.jdbc.queues")
public record JdbcQueueConfiguration(
    @Bindable(defaultValue = "PT0.025S")
    Duration minPollInterval,
    @Bindable(defaultValue = "PT0.5S")
    Duration maxPollInterval,
    @Bindable(defaultValue = "PT60S")
    Duration pollSwitchInterval,
    @Bindable(defaultValue = "100")
    Integer pollSize,
    @Bindable(defaultValue = "5")
    Integer switchSteps,
    @Bindable(defaultValue = "true")
    Boolean immediateRepoll
) {

    public List<Step> computeSteps() {
        if (this.maxPollInterval.compareTo(this.minPollInterval) < 0) {
            throw new IllegalArgumentException("'maxPollInterval' (" + this.maxPollInterval + ") must be greater than or equal to 'minPollInterval' (" + this.minPollInterval + ")");
        }

        if (this.maxPollInterval.equals(this.minPollInterval)) {
            return List.of(new Step(this.minPollInterval, Duration.ZERO));
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

    public record Step(Duration pollInterval, Duration switchInterval) implements Comparable<Step> {
        @Override
        public int compareTo(Step o) {
            return this.switchInterval.compareTo(o.switchInterval);
        }
    }
}

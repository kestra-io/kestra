package io.kestra.queue.poller;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Poller configuration:
 * 
 * @param minPollInterval the minimum poll interval
 * @param maxPollInterval the maximum poll interval
 * @param pollSwitchInterval the switch interval: after this duration, if no poll queries return an item, switch to maxPollInterval
 * @param pollSize the maximum number of items returned by a poll query, if a poll query returns this: we repoll immediately
 * @param switchSteps the number of switch steps: if minPollInterval == maxPollInterval, only one step will be computed: the minPollInterval,
 *        otherwise we compute steps by starting from maxPollInterval then divide it by 2 switchSteps times or until we reach minPollInterval.
 * @param immediateRepoll if true, when a poll query returns items, we repoll immediately
 */
public record QueuePollerConfiguration(
    Duration minPollInterval,
    Duration maxPollInterval,
    Duration pollSwitchInterval,
    Integer pollSize,
    Integer switchSteps,
    Boolean immediateRepoll) {

    /**
     * Compute the poll steps:
     * <ul>
     * <li>If maxPollInterval == minPollInterval: return a single step with minPollInterval</li>
     * <li>Otherwise, we compute steps by starting from maxPollInterval then divide it by 2 switchSteps times or until we reach minPollInterval</li>
     * </ul>
     *
     * @throws IllegalArgumentException if maxPollInteval is less than minPollInterval
     */
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

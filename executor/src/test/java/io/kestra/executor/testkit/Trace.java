package io.kestra.executor.testkit;

import java.util.List;
import java.util.stream.Stream;

/**
 * What a closed-loop run did: each delivery the real executor received and what it emitted in
 * return, numbered across queues so ordering between queues can be asserted.
 */
public record Trace(List<Step> steps) {
    public static final String KILL = "kill";
    public static final String LOOP_EXECUTION_EVENT = "loopExecutionEvent";
    public static final String WORKER_JOB_EVENT = "workerJobEvent";
    public static final String SUBFLOW_EXECUTION_RESULT = "subflowExecutionResult";
    public static final String EXECUTION = "execution";
    public static final String FOLLOW_EXECUTION_EVENT = "followExecutionEvent";
    public static final String EXECUTION_COMMAND = "executionCommand";
    public static final String EXECUTION_EVENT = "executionEvent";
    public static final String WORKER_TASK_RESULT = "workerTaskResult";
    public static final String SUBFLOW_EXECUTION_END = "subflowExecutionEnd";
    public static final String MULTIPLE_CONDITION_EVENT = "multipleConditionEvent";
    public static final String EXECUTION_STATISTIC = "executionStatistic";
    public static final String TRIGGER_EVENT = "triggerEvent";
    public static final String EXECUTION_TERMINATED = "executionTerminated";

    public record Emission(int sequence, String queue, Object message) {
        public <T> T as(Class<T> type) {
            return type.cast(message);
        }
    }

    public record Step(String queue, Object message, List<Emission> emitted) {
        public Stream<Emission> emitted(String queue) {
            return emitted.stream().filter(emission -> emission.queue().equals(queue));
        }
    }

    public Stream<Emission> emitted(String queue) {
        return steps.stream().flatMap(step -> step.emitted(queue));
    }

    public Stream<Step> deliveredFrom(String queue) {
        return steps.stream().filter(step -> step.queue().equals(queue));
    }
}

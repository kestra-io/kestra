package io.kestra.core.utils;

import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.runners.RunContext;
import io.kestra.core.models.annotations.Plugin;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task used in testing to verify that a task has been run.",
    description = "Looks up a CountDownLatch from a static registry by key and counts it down. " +
        "Optionally waits for a second latch to reach zero before counting down, allowing a test to " +
        "keep the task alive until an explicit signal is given."
)
@Plugin()
public class CountDownLatchTask extends Task implements RunnableTask<Output> {
    public static final ConcurrentHashMap<String, CountDownLatch> REGISTRY = new ConcurrentHashMap<>();

    @NotNull
    @Schema(
        title = "countDownLatchKey",
        description = "Registry key of the CountDownLatch to count down when the task runs."
    )
    Property<String> countDownLatchKey;

    @Nullable
    @Schema(
        title = "awaitCountDownLatchKey",
        description = "Registry key of an optional CountDownLatch to wait on before counting down. " +
            "The task will block until this latch reaches zero, allowing the test to control when the task finishes."
    )
    Property<String> awaitCountDownLatchKey;

    @Schema(
        title = "awaitCountDownLatchDuration",
        description = "Maximum duration to wait for the awaitCountDownLatch to reach zero."
    )
    @Builder.Default
    Property<Duration> awaitCountDownLatchDuration = Property.ofValue(DEFAULT_AWAIT_DURATION);

    private static final Duration DEFAULT_AWAIT_DURATION = Duration.ofSeconds(5);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String awaitKey = runContext.render(awaitCountDownLatchKey).as(String.class).orElse(null);
        if (awaitKey != null) {
            CountDownLatch awaitLatch = REGISTRY.get(awaitKey);
            if (awaitLatch == null) {
                throw new IllegalArgumentException("No CountDownLatch registered for awaitCountDownLatchKey: " + awaitKey);
            }
            Duration awaitDuration = runContext.render(awaitCountDownLatchDuration)
                .as(Duration.class)
                .orElse(DEFAULT_AWAIT_DURATION);

            boolean reachedZero = awaitLatch.await(awaitDuration.toMillis(), TimeUnit.MILLISECONDS);
            if (!reachedZero) {
                throw new RuntimeException("awaitCountDownLatch '" + awaitKey + "' failed to reach zero within " + awaitDuration);
            }
        }

        String key = runContext.render(countDownLatchKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Property countDownLatchKey cannot be null"));
        CountDownLatch latch = REGISTRY.get(key);
        if (latch == null) {
            throw new IllegalArgumentException("No CountDownLatch registered for countDownLatchKey: " + key);
        }
        latch.countDown();

        return null;
    }


    public static CountDownLatchTask getTaskForCountDownLatch(CountDownLatch countDownLatch, CountDownLatch awaitCountDownLatch, Duration awaitCountDownLatchDuration) {
        return getTaskForCountDownLatch(countDownLatch, awaitCountDownLatch, awaitCountDownLatchDuration, null);
    }

    @SuppressWarnings("unchecked")
    public static CountDownLatchTask getTaskForCountDownLatch(CountDownLatch countDownLatch, CountDownLatch awaitCountDownLatch, Duration awaitCountDownLatchDuration, WorkerSelector workerSelector) {
        CountDownLatchTaskBuilder<?, ?> builder = builder()
            .id("unit-test")
            .type(CountDownLatchTask.class.getName())
            .workerSelector(workerSelector);

        if (awaitCountDownLatch != null) {
            String awaitCountDownLatchKey = UUID.randomUUID().toString();
            REGISTRY.put(awaitCountDownLatchKey, awaitCountDownLatch);
            builder = builder.awaitCountDownLatchKey(Property.ofValue(awaitCountDownLatchKey))
                .awaitCountDownLatchDuration(Property.ofValue(awaitCountDownLatchDuration));
        }

        String countDownLatchKey = UUID.randomUUID().toString();
        REGISTRY.put(countDownLatchKey, countDownLatch);

        return (CountDownLatchTask) builder.countDownLatchKey(Property.ofValue(countDownLatchKey)).build();
    }
}

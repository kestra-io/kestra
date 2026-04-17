package io.kestra.core.models.tasks.runners;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class DefaultLogConsumerTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void markerLinesAreSwallowedAndNotCounted() {
        var runContext = runContextFactory.of("id", "namespace", IdUtils.create());
        var consumer = new DefaultLogConsumer(runContext);

        consumer.accept("##kestra:log:debug##", false);
        assertThat(consumer.getStdOutCount()).isZero();
        assertThat(consumer.getStdErrCount()).isZero();

        consumer.accept("##kestra:log:info##", false);
        assertThat(consumer.getStdOutCount()).isZero();
        assertThat(consumer.getStdErrCount()).isZero();
    }

    @Test
    void regularLinesAreCounted() {
        var runContext = runContextFactory.of("id", "namespace", IdUtils.create());
        var consumer = new DefaultLogConsumer(runContext);

        consumer.accept("##kestra:log:debug##", false);
        consumer.accept("some before-command output", false);
        consumer.accept("##kestra:log:info##", false);
        consumer.accept("main output", false);

        assertThat(consumer.getStdOutCount()).isEqualTo(2);
        assertThat(consumer.getStdErrCount()).isZero();
    }

    @Test
    void stderrLinesAreCountedEvenInDebugMode() {
        var runContext = runContextFactory.of("id", "namespace", IdUtils.create());
        var consumer = new DefaultLogConsumer(runContext);

        consumer.accept("##kestra:log:debug##", false);
        consumer.accept("stderr during beforeCommands", true);

        assertThat(consumer.getStdErrCount()).isEqualTo(1);
        assertThat(consumer.getStdOutCount()).isZero();
    }
}

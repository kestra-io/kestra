package io.kestra.core.worker;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerQueuesTest {

    @Test
    void shouldBeDefaultWhenIdEqualsDefaultSentinel() {
        assertThat(WorkerQueues.isDefault(WorkerQueues.DEFAULT_ID)).isTrue();
        assertThat(WorkerQueues.isDefault("default")).isTrue();
    }

    @Test
    void shouldNotBeDefaultForOtherIds() {
        assertThat(WorkerQueues.isDefault(null)).isFalse();
        assertThat(WorkerQueues.isDefault("")).isFalse();
        assertThat(WorkerQueues.isDefault("   ")).isFalse();
        assertThat(WorkerQueues.isDefault("my-queue")).isFalse();
    }

    @Test
    void shouldFormatAsDefaultPlaceholderWhenIdIsDefaultSentinel() {
        assertThat(WorkerQueues.forLog(WorkerQueues.DEFAULT_ID)).isEqualTo("(default)");
    }

    @Test
    void shouldFormatAsIdWhenNamed() {
        assertThat(WorkerQueues.forLog("my-queue")).isEqualTo("my-queue");
    }

    @Test
    void shouldPreferTagsOverIdWhenTagsPresent() {
        assertThat(WorkerQueues.forLog(List.of("docker", "linux"), "any-id"))
            .isEqualTo("tags [docker, linux]");
    }

    @Test
    void shouldFallBackToIdWhenTagsAreNullOrEmpty() {
        assertThat(WorkerQueues.forLog(null, "my-queue")).isEqualTo("my-queue");
        assertThat(WorkerQueues.forLog(List.of(), "my-queue")).isEqualTo("my-queue");
    }

    @Test
    void shouldFallBackToDefaultPlaceholderWhenTagsAbsentAndIdIsDefault() {
        assertThat(WorkerQueues.forLog(null, WorkerQueues.DEFAULT_ID)).isEqualTo("(default)");
        assertThat(WorkerQueues.forLog(List.of(), WorkerQueues.DEFAULT_ID)).isEqualTo("(default)");
    }
}

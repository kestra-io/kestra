package io.kestra.core.models.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerGroupTest {

    @Test
    void isDefaultShouldReturnTrueForNullKey() {
        assertThat(WorkerGroup.isDefault(null)).isTrue();
    }

    @Test
    void isDefaultShouldReturnTrueForEmptyKey() {
        assertThat(WorkerGroup.isDefault("")).isTrue();
    }

    @Test
    void isDefaultShouldReturnTrueForBlankKey() {
        assertThat(WorkerGroup.isDefault("   ")).isTrue();
    }

    @Test
    void isDefaultShouldReturnFalseForNamedKey() {
        assertThat(WorkerGroup.isDefault("my-group")).isFalse();
    }

    @Test
    void forLogShouldReturnDefaultPlaceholderForNullOrBlank() {
        assertThat(WorkerGroup.forLog(null)).isEqualTo("(default)");
        assertThat(WorkerGroup.forLog("")).isEqualTo("(default)");
        assertThat(WorkerGroup.forLog("  ")).isEqualTo("(default)");
    }

    @Test
    void forLogShouldReturnKeyWhenNamed() {
        assertThat(WorkerGroup.forLog("my-group")).isEqualTo("my-group");
    }
}

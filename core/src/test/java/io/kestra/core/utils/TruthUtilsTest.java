package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TruthUtilsTest {
    @Test
    void isTruthy() {
        assertThat(TruthUtils.isTruthy("true")).isTrue();
        assertThat(TruthUtils.isTruthy("  true  ")).isTrue();
        assertThat(TruthUtils.isTruthy("1")).isTrue();
        assertThat(TruthUtils.isTruthy("This should be true")).isTrue();
    }

    @Test
    void isFalsy() {
        assertThat(TruthUtils.isFalsy("false")).isTrue();
        assertThat(TruthUtils.isFalsy("     false ")).isTrue();
        assertThat(TruthUtils.isFalsy("0")).isTrue();
        assertThat(TruthUtils.isFalsy("-0")).isTrue();
        assertThat(TruthUtils.isFalsy("")).isTrue();
    }
}

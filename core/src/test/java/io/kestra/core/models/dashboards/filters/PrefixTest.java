package io.kestra.core.models.dashboards.filters;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrefixTest {

    enum TestField {
        NAMESPACE
    }

    @Test
    void shouldBuildWithCorrectType() {
        Prefix<TestField> prefix = Prefix.<TestField> builder()
            .field(TestField.NAMESPACE)
            .value("io.kestra.tests")
            .build();

        assertThat(prefix.getType()).isEqualTo(AbstractFilter.FilterType.PREFIX);
        assertThat(prefix.getField()).isEqualTo(TestField.NAMESPACE);
        assertThat(prefix.getValue()).isEqualTo("io.kestra.tests");
    }

    @Test
    void shouldHaveCorrectDefaults() {
        Prefix<TestField> prefix = Prefix.<TestField> builder()
            .field(TestField.NAMESPACE)
            .value("test")
            .build();

        assertThat(prefix.getType()).isEqualTo(AbstractFilter.FilterType.PREFIX);
    }
}

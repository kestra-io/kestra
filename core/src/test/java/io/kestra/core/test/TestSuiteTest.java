package io.kestra.core.test;

import io.kestra.core.serializers.YamlParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSuiteTest {

    @Test
    void canBe_parsed() {
        var source = """
            id: simple-return-test-suite-1-id
            namespace: io.kestra.tests
            description: assert flow is returning the input value as output
            flowId: return-flow
            testCases:
              - id: test_case_1
                type: io.kestra.core.tests.flow.UnitTest
                fixtures:
                  inputs:
                    inputA: "Hi there"
                assertions:
                  - value: "{{ outputs.return.value }}"
                    equalTo: 'Hi there'
            """;

        var parsedTestSuite = YamlParser.parse(source, TestSuite.class).toBuilder().source(source).tenantId("main").build();

        assertThat(parsedTestSuite).isNotNull();
        assertThat(parsedTestSuite).extracting(TestSuite::getId).isEqualTo("simple-return-test-suite-1-id");
    }

    @Test
    void canBe_disabled() {
        var source = """
            id: simple-return-test-suite-1-id
            namespace: io.kestra.tests
            description: assert flow is returning the input value as output
            flowId: return-flow
            testCases:
              - id: test_case_1
                type: io.kestra.core.tests.flow.UnitTest
                fixtures:
                  inputs:
                    inputA: "Hi there"
                assertions:
                  - value: "{{ outputs.return.value }}"
                    equalTo: 'Hi there'
            """;
        var parsedTestSuite = YamlParser.parse(source, TestSuite.class).toBuilder().source(source).tenantId("main").build();

        parsedTestSuite = parsedTestSuite.disable();

        assertThat(parsedTestSuite).extracting(TestSuite::isDisabled).isEqualTo(true);
        assertThat(parsedTestSuite.getSource()).contains("disabled: true");
    }

    @Test
    void canBe_enabled() {
        var source = """
            id: simple-return-test-suite-1-id
            namespace: io.kestra.tests
            description: assert flow is returning the input value as output
            flowId: return-flow
            disabled: true
            testCases:
              - id: test_case_1
                type: io.kestra.core.tests.flow.UnitTest
                fixtures:
                  inputs:
                    inputA: "Hi there"
                assertions:
                  - value: "{{ outputs.return.value }}"
                    equalTo: 'Hi there'
            """;
        var parsedTestSuite = YamlParser.parse(source, TestSuite.class).toBuilder().source(source).tenantId("main").build();

        parsedTestSuite = parsedTestSuite.enable();

        assertThat(parsedTestSuite).extracting(TestSuite::isDisabled).isEqualTo(false);
        assertThat(parsedTestSuite.getSource()).contains("disabled: false");
    }
}

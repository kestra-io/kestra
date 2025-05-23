package io.kestra.core.test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.test.flow.Assertion;
import io.kestra.core.test.flow.AssertionResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.kestra.core.test.flow.Assertion.Operator.EQUALS_TO;
import static io.kestra.core.test.flow.Assertion.Operator.IS_NOT_NULL;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class AssertionTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldAssertSuccess_equalsTo() {
        var assertion = Assertion.builder()
            .value(Property.ofValue("value1"))
            .equalsTo(Property.ofValue("value1"))
            .description(Property.ofValue("my description"))
            .build();

        assertThat(assertion.run(runContextFactory.of()).results())
            .hasSize(1)
            .first()
            .satisfies(result -> {
                assertThat(result).extracting(AssertionResult::isSuccess).isEqualTo(true);
                assertThat(result).extracting(AssertionResult::description).isEqualTo("my description");
                assertThat(result).extracting(AssertionResult::errorMessage).isNull();
            });
    }

    @Test
    void shouldAssertFail_equalsTo() {
        var assertion = Assertion.builder()
            .value(Property.ofValue("value1"))
            .equalsTo(Property.ofValue("different-value"))
            .errorMessage(Property.ofValue("error message"))
            .build();

        assertThat(assertion.run(runContextFactory.of()).results())
            .hasSize(1)
            .first()
            .satisfies(result -> {
                assertThat(result).extracting(AssertionResult::isSuccess).isEqualTo(false);
                assertThat(result).extracting(AssertionResult::errorMessage).isEqualTo("error message");
            });
    }

    @Test
    void shouldBrokenAssert_returnError() {
        var assertion = Assertion.builder()
            .value(new Property<>("{{ invalid-pebble-expression() }}"))
            .equalsTo(Property.ofValue("value"))
            .build();

        assertThat(assertion.run(runContextFactory.of()).results())
            .hasSize(0);
        assertThat(assertion.run(runContextFactory.of()).errors())
            .hasSize(1)
            .first()
            .satisfies(result -> {
                assertThat(result.message()).contains("Could not evaluate assertion");
                assertThat(result.details()).contains("invalid-pebble-expression()");
                assertThat(result.details()).contains("io.pebbletemplates.pebble.error.PebbleException");
            });
    }

    @Test
    void shouldRender_values_fromTaskOutputs() {
        var assertion = Assertion.builder()
            .value(new Property<>("{{ outputs.my_task.res }}"))
            .equalsTo(Property.ofValue("value1"))
            .build();
        var runContext = runContextFactory.of(Map.of("outputs", Map.of("my_task", Map.of("res", "value1"))));

        assertThat(assertion.run(runContext).results())
            .hasSize(1)
            .first()
            .extracting(AssertionResult::isSuccess).isEqualTo(true);
    }

    @Test
    void shouldRender_values_fromTaskOutputs_and_produce_defaultErrorMessage() {
        var assertion = Assertion.builder()
            .value(new Property<>("{{ outputs.my_task.res }}"))
            .equalsTo(Property.ofValue("expectedValue2"))
            .build();
        var runContext = runContextFactory.of(Map.of("outputs", Map.of("my_task", Map.of("res", "actualValue1"))));

        assertThat(assertion.run(runContext).results())
            .hasSize(1)
            .first()
            .satisfies(result -> {
                assertThat(result).extracting(AssertionResult::isSuccess).isEqualTo(false);
                assertThat(result).extracting(AssertionResult::errorMessage)
                    .isEqualTo("expected '{{ outputs.my_task.res }}' to equal 'expectedValue2' but was 'actualValue1'");
            });
    }

    @Test
    void endsWith_success_number() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue(1))
                .equalsTo(Property.ofValue(1))
                .build()
        );
    }

    @Test
    void equalsTo_failure_number() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue(1))
                .equalsTo(Property.ofValue(2))
                .build()
        );
    }

    @Test
    void endsWith_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue("mystring"))
                .endsWith(Property.ofValue("ing"))
                .build()
        );
    }

    @Test
    void endsWith_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue("mystring"))
                .endsWith(Property.ofValue("mys"))
                .build()
        );
    }

    @Test
    void startsWith_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue("mystring"))
                .startsWith(Property.ofValue("mys"))
                .build()
        );
    }

    @Test
    void startsWith_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue("mystring"))
                .startsWith(Property.ofValue("ing"))
                .build()
        );
    }

    @Test
    void contains_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue("mystring"))
                .contains(Property.ofValue("str"))
                .build()
        );
    }

    @Test
    void contains_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue("mystring"))
                .contains(Property.ofValue("toto"))
                .build()
        );
    }

    @Test
    void notEqualsTo_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue("value1"))
                .notEqualsTo(Property.ofValue("value2222"))
                .build()
        );
    }

    @Test
    void notEqualsTo_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue("value1"))
                .notEqualsTo(Property.ofValue("value1"))
                .build()
        );
    }

    @Test
    void greaterThan_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue(333d))
                .greaterThan(Property.ofValue(2d))
                .build()
        );
    }

    @Test
    void greaterThan_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue(2d))
                .greaterThan(Property.ofValue(333d))
                .build()
        );
    }

    @Test
    void greaterThanOrEqualTo_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue(333d))
                .greaterThanOrEqualTo(Property.ofValue(333d))
                .build()
        );
    }

    @Test
    void greaterThanOrEqualTo_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue(2d))
                .greaterThanOrEqualTo(Property.ofValue(333d))
                .build()
        );
    }

    @Test
    void lessThan_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue(2d))
                .lessThan(Property.ofValue(444d))
                .build()
        );
    }

    @Test
    void lessThan_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue(444d))
                .lessThan(Property.ofValue(2d))
                .build()
        );
    }

    @Test
    void lessThanOrEqualTo_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue(444d))
                .lessThanOrEqualTo(Property.ofValue(444d))
                .build()
        );
    }

    @Test
    void lessThanOrEqualTo_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue(444d))
                .lessThanOrEqualTo(Property.ofValue(2d))
                .build()
        );
    }

    @Test
    void in_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue("a"))
                .in(Property.ofValue(List.of("a", "b")))
                .build()
        );
    }

    @Test
    void in_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue("x"))
                .in(Property.ofValue(List.of("a", "b")))
                .build()
        );
    }

    @Test
    void notIn_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue("a"))
                .notIn(Property.ofValue(List.of("d", "e")))
                .build()
        );
    }

    @Test
    void notIn_failure() {
        testAssertionResultFails(
            Assertion.builder()
                .value(Property.ofValue("a"))
                .notIn(Property.ofValue(List.of("a", "b")))
                .build()
        );
    }

    @Test
    void isNull_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue(null))
                .isNull(Property.ofValue(true))
                .build()
        );
    }

    @Test
    void isNull_failure() {
        var testedAssertion = Assertion.builder()
            .value(Property.ofValue("value1"))
            .isNull(Property.ofValue(true))
            .build();
        assertThat(testedAssertion.run(runContextFactory.of()).results())
            .first()
            .satisfies(result -> {
                    assertThat(result).extracting(AssertionResult::isSuccess).isEqualTo(false);
                    assertThat(result).extracting(AssertionResult::errorMessage).isEqualTo("expected 'value1' to be null but was 'value1'");
                }
            );
    }

    @Test
    void isNotNull_success() {
        testAssertionResultSuccess(
            Assertion.builder()
                .value(Property.ofValue("value1"))
                .isNotNull(Property.ofValue(true))
                .build()
        );
    }

    @Test
    void isNotNull_failure() {
        var testedAssertion = Assertion.builder()
            .value(Property.ofValue(null))
            .isNotNull(Property.ofValue(true))
            .build();
        assertThat(testedAssertion.run(runContextFactory.of()).results())
            .first()
            .satisfies(result -> {
                    assertThat(result).extracting(AssertionResult::isSuccess).isEqualTo(false);
                    assertThat(result).extracting(AssertionResult::errorMessage).isEqualTo("expected 'null' to be not null but was 'null'");
                }
            );
    }

    @Test
    void isNotNull_and_isEqualTo_failure() {
        var testedAssertion = Assertion.builder()
            .value(Property.ofValue("value1"))
            .isNotNull(Property.ofValue(true))
            .equalsTo(Property.ofValue("value222"))
            .build();

        var testResults = testedAssertion.run(runContextFactory.of()).results();
        assertThat(testResults)
            .hasSize(2);
        assertThat(testResults)
            .filteredOn(res -> res.operator().equals(IS_NOT_NULL.toString()))
            .first()
            .extracting(AssertionResult::isSuccess).isEqualTo(true);
        assertThat(testResults)
            .filteredOn(res -> res.operator().equals(EQUALS_TO.toString()))
            .first()
            .extracting(AssertionResult::isSuccess).isEqualTo(false);
    }

    void testAssertionResultSuccess(Assertion testedAssertion) {
        assertThat(testedAssertion.run(runContextFactory.of()).results())
            .hasSize(1)
            .first()
            .extracting(AssertionResult::isSuccess).isEqualTo(true);
    }

    void testAssertionResultFails(Assertion testedAssertion) {
        assertThat(testedAssertion.run(runContextFactory.of()).results())
            .hasSize(1)
            .first()
            .extracting(AssertionResult::isSuccess).isEqualTo(false);
    }
}

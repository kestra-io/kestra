package io.kestra.webserver.services;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.webserver.utils.Searchable;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class FollowLogEventSearchableTest {

    @Inject
    @Named("LOG")
    private Searchable<FollowLogEvent> searchable;

    @ParameterizedTest
    @FieldSource("filtersTestCases")
    void shouldEvaluatePredicateAsExpected(PredicateCase testCase) {
        assertThat(searchable.matches(testCase.event(), List.of(testCase.filter())))
            .as(testCase.filter().field().name() + " / " + testCase.filter().operation().name())
            .isEqualTo(testCase.expected());
    }

    private static final Instant T_PAST = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant T_NOW = Instant.parse("2020-06-01T00:00:00Z");
    private static final Instant T_FUTURE = Instant.parse("2020-12-31T00:00:00Z");

    private static final FollowLogEvent baseEvent = new FollowLogEvent(
        null,                  // tenantId
        "io.kestra.demo",
        "demo-flow",
        "load-data",
        "exec-123",
        "tr-load-1",
        0,
        "demo-trigger",
        T_NOW,
        Level.INFO,
        "main",
        "hello world",
        null
    );

    private static final FollowLogEvent systemEvent = new FollowLogEvent(
        null, "system", "demo-flow", "load-data", "exec-sys",
        "tr-sys", 0, "demo-trigger", T_NOW, Level.INFO, "main", "system message", null
    );

    private static final FollowLogEvent pastEvent = new FollowLogEvent(
        null, "io.kestra.demo", "demo-flow", "load-data", "exec-past",
        "tr-past", 0, "demo-trigger", T_PAST, Level.INFO, "main", "past", null
    );

    private static final FollowLogEvent futureEvent = new FollowLogEvent(
        null, "io.kestra.demo", "demo-flow", "load-data", "exec-future",
        "tr-future", 0, "demo-trigger", T_FUTURE, Level.INFO, "main", "future", null
    );

    private static QueryFilter filter(Field field, Op op, Object value) {
        return QueryFilter.builder().field(field).operation(op).value(value).build();
    }

    private record PredicateCase(FollowLogEvent event, QueryFilter filter, boolean expected) {
    }

    @SuppressWarnings("unused")
    private static final List<PredicateCase> filtersTestCases = List.of(
        new PredicateCase(baseEvent, filter(Field.QUERY, Op.EQUALS, "hello"), true),
        new PredicateCase(baseEvent, filter(Field.QUERY, Op.EQUALS, "missing"), false),
        new PredicateCase(baseEvent, filter(Field.QUERY, Op.NOT_EQUALS, "hello"), false),
        new PredicateCase(baseEvent, filter(Field.QUERY, Op.NOT_EQUALS, "missing"), true),

        new PredicateCase(baseEvent, filter(Field.SCOPE, Op.EQUALS, FlowScope.USER), true),
        new PredicateCase(systemEvent, filter(Field.SCOPE, Op.EQUALS, FlowScope.USER), false),
        new PredicateCase(systemEvent, filter(Field.SCOPE, Op.EQUALS, FlowScope.SYSTEM), true),
        new PredicateCase(baseEvent, filter(Field.SCOPE, Op.NOT_EQUALS, FlowScope.USER), false),
        new PredicateCase(systemEvent, filter(Field.SCOPE, Op.NOT_EQUALS, FlowScope.USER), true),
        new PredicateCase(baseEvent, filter(Field.SCOPE, Op.IN, List.of(FlowScope.USER, FlowScope.SYSTEM)), true),
        new PredicateCase(baseEvent, filter(Field.SCOPE, Op.IN, List.of(FlowScope.SYSTEM)), false),
        new PredicateCase(baseEvent, filter(Field.SCOPE, Op.NOT_IN, List.of(FlowScope.SYSTEM)), true),
        new PredicateCase(baseEvent, filter(Field.SCOPE, Op.NOT_IN, List.of(FlowScope.USER, FlowScope.SYSTEM)), false),

        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.EQUALS, "io.kestra.demo"), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.EQUALS, "other"), false),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.NOT_EQUALS, "other"), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.NOT_EQUALS, "io.kestra.demo"), false),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.CONTAINS, "kestra"), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.CONTAINS, "missing"), false),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.STARTS_WITH, "io.kestra"), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.STARTS_WITH, "kestra"), false),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.ENDS_WITH, "demo"), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.ENDS_WITH, "io"), false),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.REGEX, "io\\.kestra\\..*"), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.REGEX, "no\\..*"), false),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.IN, List.of("io.kestra.demo", "other")), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.IN, List.of("nothing", "other")), false),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.NOT_IN, List.of("nothing", "other")), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.NOT_IN, List.of("io.kestra.demo")), false),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.PREFIX, "io.kestra"), true),
        new PredicateCase(baseEvent, filter(Field.NAMESPACE, Op.PREFIX, "io.kestrasomething"), false),

        new PredicateCase(baseEvent, filter(Field.START_DATE, Op.GREATER_THAN_OR_EQUAL_TO, T_NOW), true),
        new PredicateCase(pastEvent, filter(Field.START_DATE, Op.GREATER_THAN_OR_EQUAL_TO, T_NOW), false),
        new PredicateCase(futureEvent, filter(Field.START_DATE, Op.GREATER_THAN, T_NOW), true),
        new PredicateCase(baseEvent, filter(Field.START_DATE, Op.GREATER_THAN, T_NOW), false),
        new PredicateCase(baseEvent, filter(Field.START_DATE, Op.LESS_THAN_OR_EQUAL_TO, T_NOW), true),
        new PredicateCase(futureEvent, filter(Field.START_DATE, Op.LESS_THAN_OR_EQUAL_TO, T_NOW), false),
        new PredicateCase(pastEvent, filter(Field.START_DATE, Op.LESS_THAN, T_NOW), true),
        new PredicateCase(baseEvent, filter(Field.START_DATE, Op.LESS_THAN, T_NOW), false),
        new PredicateCase(baseEvent, filter(Field.START_DATE, Op.EQUALS, T_NOW), true),
        new PredicateCase(pastEvent, filter(Field.START_DATE, Op.EQUALS, T_NOW), false),
        new PredicateCase(pastEvent, filter(Field.START_DATE, Op.NOT_EQUALS, T_NOW), true),
        new PredicateCase(baseEvent, filter(Field.START_DATE, Op.NOT_EQUALS, T_NOW), false),

        new PredicateCase(baseEvent, filter(Field.END_DATE, Op.GREATER_THAN_OR_EQUAL_TO, T_NOW), true),
        new PredicateCase(pastEvent, filter(Field.END_DATE, Op.GREATER_THAN_OR_EQUAL_TO, T_NOW), false),
        new PredicateCase(futureEvent, filter(Field.END_DATE, Op.GREATER_THAN, T_NOW), true),
        new PredicateCase(baseEvent, filter(Field.END_DATE, Op.GREATER_THAN, T_NOW), false),
        new PredicateCase(baseEvent, filter(Field.END_DATE, Op.LESS_THAN_OR_EQUAL_TO, T_NOW), true),
        new PredicateCase(futureEvent, filter(Field.END_DATE, Op.LESS_THAN_OR_EQUAL_TO, T_NOW), false),
        new PredicateCase(pastEvent, filter(Field.END_DATE, Op.LESS_THAN, T_NOW), true),
        new PredicateCase(baseEvent, filter(Field.END_DATE, Op.LESS_THAN, T_NOW), false),
        new PredicateCase(baseEvent, filter(Field.END_DATE, Op.EQUALS, T_NOW), true),
        new PredicateCase(pastEvent, filter(Field.END_DATE, Op.EQUALS, T_NOW), false),
        new PredicateCase(pastEvent, filter(Field.END_DATE, Op.NOT_EQUALS, T_NOW), true),
        new PredicateCase(baseEvent, filter(Field.END_DATE, Op.NOT_EQUALS, T_NOW), false),

        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.EQUALS, "demo-flow"), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.EQUALS, "other"), false),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.NOT_EQUALS, "other"), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.NOT_EQUALS, "demo-flow"), false),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.CONTAINS, "demo"), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.CONTAINS, "nope"), false),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.STARTS_WITH, "demo"), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.STARTS_WITH, "flow"), false),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.ENDS_WITH, "flow"), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.ENDS_WITH, "demo"), false),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.REGEX, "demo-.*"), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.REGEX, "x-.*"), false),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.IN, List.of("demo-flow", "other")), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.IN, List.of("nothing")), false),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.NOT_IN, List.of("nothing")), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.NOT_IN, List.of("demo-flow")), false),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.PREFIX, "demo-flow"), true),
        new PredicateCase(baseEvent, filter(Field.FLOW_ID, Op.PREFIX, "other"), false),

        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.EQUALS, "demo-trigger"), true),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.EQUALS, "other"), false),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.NOT_EQUALS, "other"), true),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.NOT_EQUALS, "demo-trigger"), false),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.CONTAINS, "trigger"), true),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.CONTAINS, "nope"), false),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.STARTS_WITH, "demo"), true),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.STARTS_WITH, "trigger"), false),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.ENDS_WITH, "trigger"), true),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.ENDS_WITH, "demo"), false),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.IN, List.of("demo-trigger", "other")), true),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.IN, List.of("nothing")), false),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.NOT_IN, List.of("nothing")), true),
        new PredicateCase(baseEvent, filter(Field.TRIGGER_ID, Op.NOT_IN, List.of("demo-trigger")), false),

        new PredicateCase(baseEvent, filter(Field.LEVEL, Op.GREATER_THAN_OR_EQUAL_TO, Level.INFO), true),
        new PredicateCase(baseEvent, filter(Field.LEVEL, Op.GREATER_THAN_OR_EQUAL_TO, Level.ERROR), false),
        new PredicateCase(baseEvent, filter(Field.LEVEL, Op.LESS_THAN_OR_EQUAL_TO, Level.INFO), true),
        new PredicateCase(baseEvent, filter(Field.LEVEL, Op.LESS_THAN_OR_EQUAL_TO, Level.TRACE), false),

        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.EQUALS, "exec-123"), true),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.EQUALS, "other"), false),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.NOT_EQUALS, "other"), true),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.NOT_EQUALS, "exec-123"), false),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.CONTAINS, "exec"), true),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.CONTAINS, "nope"), false),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.STARTS_WITH, "exec"), true),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.STARTS_WITH, "123"), false),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.ENDS_WITH, "123"), true),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.ENDS_WITH, "exec"), false),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.IN, List.of("exec-123", "other")), true),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.IN, List.of("nothing")), false),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.NOT_IN, List.of("nothing")), true),
        new PredicateCase(baseEvent, filter(Field.EXECUTION_ID, Op.NOT_IN, List.of("exec-123")), false),

        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.EQUALS, "load-data"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.EQUALS, "other"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.NOT_EQUALS, "other"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.NOT_EQUALS, "load-data"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.CONTAINS, "load"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.CONTAINS, "nope"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.STARTS_WITH, "load"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.STARTS_WITH, "data"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.ENDS_WITH, "data"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.ENDS_WITH, "load"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.IN, List.of("load-data", "other")), true),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.IN, List.of("nothing")), false),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.NOT_IN, List.of("nothing")), true),
        new PredicateCase(baseEvent, filter(Field.TASK_ID, Op.NOT_IN, List.of("load-data")), false),

        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.EQUALS, "tr-load-1"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.EQUALS, "other"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.NOT_EQUALS, "other"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.NOT_EQUALS, "tr-load-1"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.CONTAINS, "load"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.CONTAINS, "nope"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.STARTS_WITH, "tr-"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.STARTS_WITH, "load"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.ENDS_WITH, "1"), true),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.ENDS_WITH, "tr"), false),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.IN, List.of("tr-load-1", "other")), true),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.IN, List.of("nothing")), false),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.NOT_IN, List.of("nothing")), true),
        new PredicateCase(baseEvent, filter(Field.TASK_RUN_ID, Op.NOT_IN, List.of("tr-load-1")), false),

        new PredicateCase(baseEvent, filter(Field.ATTEMPT_NUMBER, Op.EQUALS, 0), true),
        new PredicateCase(baseEvent, filter(Field.ATTEMPT_NUMBER, Op.EQUALS, 1), false),
        new PredicateCase(baseEvent, filter(Field.ATTEMPT_NUMBER, Op.NOT_EQUALS, 1), true),
        new PredicateCase(baseEvent, filter(Field.ATTEMPT_NUMBER, Op.NOT_EQUALS, 0), false),
        new PredicateCase(baseEvent, filter(Field.ATTEMPT_NUMBER, Op.IN, List.of(0, 1)), true),
        new PredicateCase(baseEvent, filter(Field.ATTEMPT_NUMBER, Op.IN, List.of(1, 2)), false),
        new PredicateCase(baseEvent, filter(Field.ATTEMPT_NUMBER, Op.NOT_IN, List.of(1, 2)), true),
        new PredicateCase(baseEvent, filter(Field.ATTEMPT_NUMBER, Op.NOT_IN, List.of(0, 1)), false)
    );
}

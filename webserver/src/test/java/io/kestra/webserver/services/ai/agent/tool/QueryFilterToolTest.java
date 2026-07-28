package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;
import io.kestra.webserver.converters.QueryFilterFormat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import static org.assertj.core.api.Assertions.assertThat;

class QueryFilterToolTest {

    static final class SampleLogsTool {
        List<QueryFilter> capturedFilters;
        String capturedExecutionId;

        @Tool(name = "read-execution-logs", value = "Read execution logs, optionally filtered.")
        public String read(
            @P("The execution id") String executionId,
            @QueryFilterFormat(QueryFilter.Resource.LOG) List<QueryFilter> filters) {
            this.capturedExecutionId = executionId;
            this.capturedFilters = filters;
            return "ok:" + executionId + ":" + filters.size();
        }
    }

    static final class TypedLogsTool {
        record Result(String executionId, int matched) {
        }

        @Tool(name = "read-execution-logs", value = "Read execution logs, optionally filtered.")
        public Result read(
            @P("The execution id") String executionId,
            @QueryFilterFormat(QueryFilter.Resource.LOG) List<QueryFilter> filters) {
            return new Result(executionId, filters.size());
        }
    }

    private static Method readMethod() {
        return toolMethodOf(SampleLogsTool.class);
    }

    private static Method toolMethodOf(final Class<?> type) {
        for (Method m : type.getMethods()) {
            if (m.isAnnotationPresent(Tool.class)) {
                return m;
            }
        }
        throw new AssertionError("no @Tool method");
    }

    @Test
    void shouldExpandFilterParamIntoPerFieldSchema() {
        // When
        ToolSpecification spec = AiToolSpecifications.toolSpecificationFrom(readMethod());

        // Then — the generic "filters" array is gone; each LOG field is its own {operator,value} object
        JsonObjectSchema params = (JsonObjectSchema) spec.parameters();
        assertThat(params.properties()).containsKey("executionId");
        assertThat(params.properties()).doesNotContainKey("filters");
        assertThat(params.properties()).containsKeys("TASK_ID", "LEVEL", "NAMESPACE");
        // The per-field operator enum is scoped to that field's supportedOp()
        JsonObjectSchema level = (JsonObjectSchema) params.properties().get("LEVEL");
        assertThat(level.properties()).containsKeys("operator", "value");
        // TIME_RANGE is never exposed (temporal handling is being reworked)
        assertThat(params.properties()).doesNotContainKey("TIME_RANGE");
        // The description gains the value/date-format guidance
        assertThat(spec.description()).contains("ISO-8601", "PT5M");
        System.out.println("=== expanded parameters ===\n" + params);
    }

    @Test
    void shouldNotExposeTimeRangeField() {
        // When / Then — TIME_RANGE is filtered out; ordinary fields are exposed
        assertThat(AiToolSpecifications.isExposedFilterField(QueryFilter.Field.TIME_RANGE)).isFalse();
        assertThat(AiToolSpecifications.isExposedFilterField(QueryFilter.Field.START_DATE)).isTrue();
    }

    @Test
    void shouldReassemblePerFieldArgumentsIntoQueryFilterList() {
        // Given
        SampleLogsTool tool = new SampleLogsTool();
        QueryFilterToolExecutor executor = new QueryFilterToolExecutor(tool, readMethod());
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("call-1")
            .name("read-execution-logs")
            .arguments("""
                {
                  "executionId": "exec-1",
                  "TASK_ID": { "operator": "EQUALS", "value": "load" },
                  "LEVEL":   { "operator": "GREATER_THAN_OR_EQUAL_TO", "value": "WARN" }
                }""")
            .build();

        // When
        String result = executor.execute(request, "mem-1");

        // Then — reassembled into two leaf QueryFilters, bound alongside executionId
        assertThat(result).isEqualTo("ok:exec-1:2");
        assertThat(tool.capturedExecutionId).isEqualTo("exec-1");
        assertThat(tool.capturedFilters)
            .extracting(f -> f.field() + "/" + f.operation() + "/" + f.value())
            .containsExactlyInAnyOrder("TASK_ID/EQUALS/load", "LEVEL/GREATER_THAN_OR_EQUAL_TO/WARN");
    }

    @Test
    void shouldSerializeStrongTypedResultToJson() {
        // Given — a filter tool that returns a strong type instead of a String
        TypedLogsTool tool = new TypedLogsTool();
        QueryFilterToolExecutor executor = new QueryFilterToolExecutor(tool, toolMethodOf(TypedLogsTool.class));
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("call-3")
            .name("read-execution-logs")
            .arguments("""
                {
                  "executionId": "exec-1",
                  "TASK_ID": { "operator": "EQUALS", "value": "load" }
                }""")
            .build();

        // When
        String result = executor.execute(request, "mem-1");

        // Then — the record is serialized to JSON for the model (not Record.toString())
        assertThat(result).isEqualTo("{\"executionId\":\"exec-1\",\"matched\":1}");
    }

    @Test
    void shouldRejectUnsupportedOperatorForField() {
        // Given — LEVEL does not support EQUALS
        SampleLogsTool tool = new SampleLogsTool();
        QueryFilterToolExecutor executor = new QueryFilterToolExecutor(tool, readMethod());
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("call-2").name("read-execution-logs")
            .arguments("{ \"LEVEL\": { \"operator\": \"EQUALS\", \"value\": \"WARN\" } }")
            .build();

        // When / Then
        try {
            executor.execute(request, "mem");
            throw new AssertionError("expected rejection");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("not supported for field 'LEVEL'");
        }
    }
}

package io.kestra.core.services;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.ReplayOverrideService.Kind;
import io.kestra.core.services.ReplayOverrideService.ReplayValue;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
@WireMockTest(httpPort = 28183)
class ReplayOverrideServiceTest {
    private static final Flow FLOW = YamlParser.parse("""
        id: replay-calls
        namespace: io.kestra.tests

        variables:
          code: KR-0412
          stock: "{{ http('http://localhost:28183/stock') }}"

        tasks:
          - id: reserve
            type: io.kestra.plugin.core.debug.Return
            format: "{{ http('http://localhost:28183/stock') }} {{ render(vars.stock) }} {{ now() }} {% for i in [1] %}{{ inputs.day }}{% endfor %} {{ inputs.day }} {{ vars.code }}"
        """, Flow.class);

    @Inject
    private ReplayOverrideService replayOverrideService;

    @Test
    void shouldLockCallsVolatileValuesAndExpressionsAlsoUsedInBlocksWithoutCallingAnything() throws Exception {
        Execution execution = execution();

        List<ReplayValue> values = replayOverrideService.values(FLOW, execution, taskRunId(execution));

        assertThat(values).extracting(ReplayValue::kind).containsExactly(Kind.CALL, Kind.EXPRESSION, Kind.VOLATILE, Kind.BLOCK, Kind.EXPRESSION);
        assertThat(values).extracting(ReplayValue::value).containsExactly(null, null, null, null, "KR-0412");
        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void shouldRejectOverridesWhenNoValueDiffersFromWhatWasRendered() {
        Execution execution = execution();

        assertThatThrownBy(() -> replayOverrideService.overrides(FLOW, execution, taskRunId(execution), Map.of("vars.code", "KR-0412")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no value differs");
    }

    private static Execution execution() {
        Execution execution = TestsUtils.mockExecution(FLOW, Map.of("day", "2026-10-12"));
        TaskRun taskRun = TestsUtils.mockTaskRun(execution, FLOW.getTasks().getFirst());
        return execution.withTaskRunList(List.of(taskRun));
    }

    private static String taskRunId(Execution execution) {
        return execution.getTaskRunList().getFirst().getId();
    }
}

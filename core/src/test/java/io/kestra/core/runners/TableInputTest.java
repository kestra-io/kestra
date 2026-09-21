package io.kestra.core.runners;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InputOutputValidationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.flows.input.IntInput;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.flows.input.TableInput;
import io.kestra.core.utils.IdUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class TableInputTest {

    private static final Execution EXECUTION = Execution.builder()
        .id(IdUtils.create())
        .flowId(IdUtils.create())
        .flowRevision(1)
        .namespace("io.kestra.test")
        .build();

    @Inject
    FlowInputOutput flowInputOutput;

    private static TableInput disks(TableInput.Rows rows) {
        return TableInput.builder()
            .id("disks")
            .type(Type.TABLE)
            .rows(rows)
            .columns(List.of(
                IntInput.builder().id("size_gb").type(Type.INT).min(10).max(2048).build(),
                StringInput.builder().id("mountpoint").type(Type.STRING).build()
            ))
            .build();
    }

    @Test
    void shouldTypeEveryCellAgainstItsColumnWhenValueIsJson() {
        List<Input<?>> inputs = List.of(disks(null));
        Map<String, Object> data = Map.of("disks", """
            [{"size_gb": "10", "mountpoint": "/dev/sda"}, {"size_gb": 20, "mountpoint": "/dev/sdb"}]""");

        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, EXECUTION, data);

        assertThat(values.getFirst().value()).isEqualTo(List.of(
            Map.of("size_gb", 10, "mountpoint", "/dev/sda"),
            Map.of("size_gb", 20, "mountpoint", "/dev/sdb")
        ));
    }

    @Test
    void shouldReportEveryOffendingCellWithItsPathWhenCellsAreInvalid() {
        List<Input<?>> inputs = List.of(disks(null));
        Map<String, Object> data = Map.of("disks", List.of(
            Map.of("size_gb", 100, "mountpoint", "/dev/sda"),
            Map.of("size_gb", 4096, "mountpoint", "/dev/sdb"),
            Map.of("mountpoint", "/dev/sdc")
        ));

        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, EXECUTION, data);

        assertThat(values.getFirst().exceptions())
            .extracting(InputOutputValidationException::getPath)
            .containsExactlyInAnyOrder("disks[1].size_gb", "disks[2].size_gb");
    }

    @Test
    void shouldRejectRowCountOutsideBoundsWhenRowsAreDeclared() {
        List<Input<?>> inputs = List.of(disks(new TableInput.Rows(2, null)));
        Map<String, Object> data = Map.of("disks", List.of(Map.of("size_gb", 10, "mountpoint", "/dev/sda")));

        List<InputAndValue> values = flowInputOutput.resolveInputs(inputs, null, EXECUTION, data);

        assertThat(values.getFirst().exceptions())
            .singleElement()
            .extracting(InputOutputValidationException::getMessage, InputOutputValidationException::getPath)
            .containsExactly("Invalid value for input `disks`. Cause: it must have at least 2 row(s)", null);
    }
}

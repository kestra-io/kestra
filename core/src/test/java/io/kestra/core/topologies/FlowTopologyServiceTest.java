package io.kestra.core.topologies;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.serializers.YamlParser;
import io.kestra.plugin.core.condition.ExecutionFlow;
import io.kestra.plugin.core.condition.ExecutionStatus;
import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.plugin.core.condition.Expression;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Parallel;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class FlowTopologyServiceTest {

    @Inject
    private FlowTopologyService flowTopologyService;

    @Test
    void flowTask() {
        Flow parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(
                Parallel.builder()
                    .id("para")
                    .type(Parallel.class.getName())
                    .tasks(List.of(Subflow.builder()
                        .id("launch")
                        .type(Subflow.class.getName())
                        .namespace("io.kestra.ee")
                        .flowId("child")
                        .build()
                    ))
                    .build()
            ))
            .build();

        FlowWithSource child = FlowWithSource.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        assertThat(flowTopologyService.isChild(parent, child)).isEqualTo(FlowRelation.FLOW_TASK);
    }

    @Test
    void noRelation() {
        Flow parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        Flow child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        assertThat(flowTopologyService.isChild(parent, child)).isNull();
    }

    @Test
    void trigger() {
        Flow parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        Flow child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .triggers(List.of(
                io.kestra.plugin.core.trigger.Flow.builder()
                    .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                    .conditions(List.of(
                        ExecutionFlow.builder()
                            .namespace(Property.ofValue("io.kestra.ee"))
                            .flowId(Property.ofValue("parent"))
                            .build(),
                        ExecutionStatus.builder()
                            .in(Property.ofValue(List.of(State.Type.SUCCESS)))
                            .build()
                    ))
                    .build()
            ))
            .build();

        assertThat(flowTopologyService.isChild(parent, child)).isEqualTo(FlowRelation.FLOW_TRIGGER);
    }

    @Test
    void multipleCondition() {
        Flow parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        Flow noTrigger = Flow.builder()
            .namespace("io.kestra.exclude")
            .id("no")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        Flow child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .triggers(List.of(
                io.kestra.plugin.core.trigger.Flow.builder()
                    .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                    .conditions(List.of(
                        ExecutionStatus.builder()
                            .in(Property.ofValue(List.of(State.Type.SUCCESS)))
                            .type(ExecutionStatus.class.getName())
                            .build(),
                        MultipleCondition.builder()
                            .type(MultipleCondition.class.getName())
                            .conditions(Map.of(
                                "first", ExecutionFlow.builder()
                                    .namespace(Property.ofValue("io.kestra.ee"))
                                    .flowId(Property.ofValue("parent"))
                                    .build(),
                                "second", ExecutionFlow.builder()
                                    .namespace(Property.ofValue("io.kestra.others"))
                                    .flowId(Property.ofValue("invalid"))
                                    .build(),
                                "filtered", ExecutionStatus.builder()
                                    .in(Property.ofValue(List.of(State.Type.SUCCESS)))
                                    .build(),
                                "variables", Expression.builder()
                                    .expression(Property.ofExpression("{{ true }}"))
                                    .build()
                            ))
                            .build()

                    ))
                    .build()
            ))
            .build();

        assertThat(flowTopologyService.isChild(parent, child)).isEqualTo(FlowRelation.FLOW_TRIGGER);

        assertThat(flowTopologyService.isChild(noTrigger, child)).isNull();
    }

    @Test
    void preconditions() {
        Flow parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        Flow noTrigger = Flow.builder()
            .namespace("io.kestra.exclude")
            .id("no")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        Flow child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .triggers(List.of(
                io.kestra.plugin.core.trigger.Flow.builder()
                    .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                    .preconditions(io.kestra.plugin.core.trigger.Flow.Preconditions.builder()
                        .flows(List.of(
                            io.kestra.plugin.core.trigger.Flow.UpstreamFlow.builder().namespace("io.kestra.ee").flowId("parent").build(),
                            io.kestra.plugin.core.trigger.Flow.UpstreamFlow.builder().namespace("io.kestra.others").flowId("invalid").build()
                        ))
                        // add an always true condition to validate that it's an AND between 'flows' and 'where'
                        .where(List.of(io.kestra.plugin.core.trigger.Flow.ExecutionFilter.builder()
                                .filters(List.of(io.kestra.plugin.core.trigger.Flow.Filter.builder().field(io.kestra.plugin.core.trigger.Flow.Field.EXPRESSION).type(io.kestra.plugin.core.trigger.Flow.Type.IS_NOT_NULL).value("something").build()))
                            .build()))
                        .build()
                    )
                    .build()
            ))
            .build();

        assertThat(flowTopologyService.isChild(parent, child)).isEqualTo(FlowRelation.FLOW_TRIGGER);

        assertThat(flowTopologyService.isChild(noTrigger, child)).isNull();
    }

    @Test
    void self1() throws IOException {
        Flow flow = parse("flows/valids/trigger-multiplecondition-listener.yaml").toBuilder().revision(1).build();

        assertThat(flowTopologyService.isChild(flow, flow)).isNull();
    }

    @Test
    void self() throws IOException {
        Flow flow = parse("flows/valids/trigger-flow-listener.yaml").toBuilder().revision(1).build();
        assertThat(flowTopologyService.isChild(flow, flow)).isNull();
    }

    private Return returnTask() {
        return Return.builder()
            .id("return")
            .type(Return.class.getName())
            .format(Property.ofValue("ok"))
            .build();
    }

    private Flow parse(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(Files.readString(file.toPath()), Flow.class);
    }
}
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
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
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

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TASK));
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

        assertThat(flowTopologyService.isChild(parent, child), nullValue());
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
                            .namespace(Property.of("io.kestra.ee"))
                            .flowId(Property.of("parent"))
                            .build(),
                        ExecutionStatus.builder()
                            .in(Property.of(List.of(State.Type.SUCCESS)))
                            .build()
                    ))
                    .build()
            ))
            .build();

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TRIGGER));
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
                            .in(Property.of(List.of(State.Type.SUCCESS)))
                            .type(ExecutionStatus.class.getName())
                            .build(),
                        MultipleCondition.builder()
                            .type(MultipleCondition.class.getName())
                            .conditions(Map.of(
                                "first", ExecutionFlow.builder()
                                    .namespace(Property.of("io.kestra.ee"))
                                    .flowId(Property.of("parent"))
                                    .build(),
                                "second", ExecutionFlow.builder()
                                    .namespace(Property.of("io.kestra.others"))
                                    .flowId(Property.of("invalid"))
                                    .build(),
                                "filtered", ExecutionStatus.builder()
                                    .in(Property.of(List.of(State.Type.SUCCESS)))
                                    .build(),
                                "variables", Expression.builder()
                                    .expression(new Property<>("{{ true }}"))
                                    .build()
                            ))
                            .build()

                    ))
                    .build()
            ))
            .build();

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TRIGGER));

        assertThat(flowTopologyService.isChild(noTrigger, child), nullValue());
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
                        .build()
                    )
                    .build()
            ))
            .build();

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TRIGGER));

        assertThat(flowTopologyService.isChild(noTrigger, child), nullValue());
    }

    @Test
    void self1() throws IOException {
        Flow flow = parse("flows/valids/trigger-multiplecondition-listener.yaml").toBuilder().revision(1).build();

        assertThat(flowTopologyService.isChild(flow, flow), nullValue());
    }

    @Test
    void self() throws IOException {
        Flow flow = parse("flows/valids/trigger-flow-listener.yaml").toBuilder().revision(1).build();
        assertThat(flowTopologyService.isChild(flow, flow), nullValue());
    }

    private Return returnTask() {
        return Return.builder()
            .id("return")
            .type(Return.class.getName())
            .format(Property.of("ok"))
            .build();
    }

    private Flow parse(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(Files.readString(file.toPath()), Flow.class);
    }
}
package io.kestra.core.services;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.core.condition.ExecutionFlow;
import io.kestra.plugin.core.condition.ExecutionStatus;
import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.plugin.core.condition.Expression;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.serializers.YamlParser;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Parallel;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
class FlowTopologyServiceTest {
    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private YamlParser yamlParser = new YamlParser();

    @Test
    void flowTask() {
        FlowWithSource parent = Flow.builder()
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
            .build()
            .withSource(null);

        FlowWithSource child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build()
            .withSource(null);

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TASK));
    }

    @Test
    void noRelation() {
        FlowWithSource parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build()
            .withSource(null);

        FlowWithSource child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build()
            .withSource(null);

        assertThat(flowTopologyService.isChild(parent, child), nullValue());
    }

    @Test
    void trigger() {
        FlowWithSource parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build()
            .withSource(null);

        FlowWithSource child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .triggers(List.of(
                io.kestra.plugin.core.trigger.Flow.builder()
                    .conditions(List.of(
                        ExecutionFlow.builder()
                            .namespace("io.kestra.ee")
                            .flowId("parent")
                            .build(),
                        ExecutionStatus.builder()
                            .in(List.of(State.Type.SUCCESS))
                            .build()
                    ))
                    .build()
            ))
            .build()
            .withSource(null);

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TRIGGER));
    }

    @Test
    void multipleCondition() {
        FlowWithSource parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build()
            .withSource(null);

        FlowWithSource noTrigger = Flow.builder()
            .namespace("io.kestra.exclude")
            .id("no")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build()
            .withSource(null);

        FlowWithSource child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .triggers(List.of(
                io.kestra.plugin.core.trigger.Flow.builder()
                    .conditions(List.of(
                        ExecutionStatus.builder()
                            .in(List.of(State.Type.SUCCESS))
                            .build(),
                        MultipleCondition.builder()
                            .conditions(Map.of(
                                "first", ExecutionFlow.builder()
                                    .namespace("io.kestra.ee")
                                    .flowId("parent")
                                    .build(),
                                "second", ExecutionFlow.builder()
                                    .namespace("io.kestra.others")
                                    .flowId("invalid")
                                    .build(),
                                "filtered", ExecutionStatus.builder()
                                    .in(List.of(State.Type.SUCCESS))
                                    .build(),
                                "variables", Expression.builder()
                                    .expression("{{ true }}")
                                    .build()
                            ))
                            .build()

                    ))
                    .build()
            ))
            .build()
            .withSource(null);

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TRIGGER));

        assertThat(flowTopologyService.isChild(noTrigger, child), nullValue());
    }

    @Test
    void preconditions() {
        FlowWithSource parent = Flow.builder()
            .namespace("io.kestra.ee")
            .id("parent")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build()
            .withSource(null);

        FlowWithSource noTrigger = Flow.builder()
            .namespace("io.kestra.exclude")
            .id("no")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build()
            .withSource(null);

        FlowWithSource child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .triggers(List.of(
                io.kestra.plugin.core.trigger.Flow.builder()
                    .preconditions(io.kestra.plugin.core.trigger.Flow.Preconditions.builder()
                        .flows(List.of(
                            io.kestra.plugin.core.trigger.Flow.UpstreamFlow.builder().namespace("io.kestra.ee").flowId("parent").build(),
                            io.kestra.plugin.core.trigger.Flow.UpstreamFlow.builder().namespace("io.kestra.others").flowId("invalid").build()
                        ))
                        .build()
                    )
                    .build()
            ))
            .build()
            .withSource(null);

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TRIGGER));

        assertThat(flowTopologyService.isChild(noTrigger, child), nullValue());
    }

    @Test
    void self1() {
        FlowWithSource flow = parse("flows/valids/trigger-multiplecondition-listener.yaml").toBuilder().revision(1).build().withSource(null);

        assertThat(flowTopologyService.isChild(flow, flow), nullValue());
    }

    @Test
    void self() {
        FlowWithSource flow = parse("flows/valids/trigger-flow-listener.yaml").toBuilder().revision(1).build().withSource(null);

        assertThat(flowTopologyService.isChild(flow, flow), nullValue());
    }

    private Return returnTask() {
        return Return.builder()
            .id("return")
            .type(Return.class.getName())
            .format(Property.of("ok"))
            .build();
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlParser.parse(file, Flow.class);
    }
}
package io.kestra.core.services;

import io.kestra.plugin.core.condition.ExecutionFlowCondition;
import io.kestra.plugin.core.condition.ExecutionStatusCondition;
import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.plugin.core.condition.ExpressionCondition;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Parallel;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@MicronautTest
class FlowTopologyServiceTest {
    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Test
    public void flowTask() {
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

        Flow child = Flow.builder()
            .namespace("io.kestra.ee")
            .id("child")
            .revision(1)
            .tasks(List.of(returnTask()))
            .build();

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TASK));
    }

    @Test
    public void noRelation() {
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
    public void trigger() {
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
                    .conditions(List.of(
                        ExecutionFlowCondition.builder()
                            .namespace("io.kestra.ee")
                            .flowId("parent")
                            .build(),
                        ExecutionStatusCondition.builder()
                            .in(List.of(State.Type.SUCCESS))
                            .build()
                    ))
                    .build()
            ))
            .build();

        assertThat(flowTopologyService.isChild(parent, child), is(FlowRelation.FLOW_TRIGGER));
    }

    @Test
    public void multipleCondition() {
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
                    .conditions(List.of(
                        ExecutionStatusCondition.builder()
                            .in(List.of(State.Type.SUCCESS))
                            .build(),
                        MultipleCondition.builder()
                            .conditions(Map.of(
                                "first", ExecutionFlowCondition.builder()
                                    .namespace("io.kestra.ee")
                                    .flowId("parent")
                                    .build(),
                                "second", ExecutionFlowCondition.builder()
                                    .namespace("io.kestra.others")
                                    .flowId("invalid")
                                    .build(),
                                "filtered", ExecutionStatusCondition.builder()
                                    .in(List.of(State.Type.SUCCESS))
                                    .build(),
                                "variables", ExpressionCondition.builder()
                                    .expression("{{ true }}")
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
    public void self1() {
        Flow flow = parse("flows/valids/trigger-multiplecondition-listener.yaml").toBuilder().revision(1).build();

        assertThat(flowTopologyService.isChild(flow, flow), nullValue());
    }

    @Test
    public void self() {
        Flow flow = parse("flows/valids/trigger-flow-listener.yaml").toBuilder().revision(1).build();

        assertThat(flowTopologyService.isChild(flow, flow), nullValue());
    }

    private Return returnTask() {
        return Return.builder()
            .id("return")
            .type(Return.class.getName())
            .format("ok")
            .build();
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file, Flow.class);
    }
}
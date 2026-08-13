package io.kestra.core.serializers;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.GraphTrigger;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.hierarchies.SubflowGraphTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.log.Log;
import io.kestra.plugin.core.trigger.Schedule;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class ObjectMapperFactoryTest {
    @Inject
    ObjectMapper objectMapper;

    @Data
    @NoArgsConstructor
    @JsonPropertyOrder(alphabetic = true)
    public static class Bean {
        private int intNull;
        private int intDefault = 0;
        private int intChange = 0;

        private Integer integerNull;
        private Integer integerDefault = 0;
        private Integer integerChange = 0;

        private boolean boolNull;
        private boolean boolDefaultTrue = true;
        private boolean boolChangeTrue = true;
        private boolean boolDefaultFalse = false;
        private boolean boolChangeFalse = false;

        private Boolean booleanNull;
        private Boolean booleanDefaultTrue = true;
        private Boolean booleanChangeTrue = true;
        private Boolean booleanDefaultFalse = false;
        private Boolean booleanChangeFalse = false;

        private String stringNull;
        private String stringDefault = "bla";
        private String stringChange = "bla";

        private Duration duration;
        private ZonedDateTime zonedDateTime;
    }

    @Test
    void serialize() throws JacksonException {
        Bean b = new Bean();

        b.setIntChange(1);
        b.setIntegerChange(1);
        b.setBoolChangeTrue(false);
        b.setBoolChangeFalse(true);
        b.setBooleanChangeTrue(false);
        b.setBooleanChangeFalse(true);
        b.setStringChange("foo");

        b.setDuration(Duration.parse("PT5M"));
        b.setZonedDateTime(ZonedDateTime.parse("2013-09-08T16:19:12.000000+02:00"));

        String s = objectMapper.writeValueAsString(b);

        assertThat(s).contains("\"intNull\":0");
        assertThat(s).contains("\"intDefault\":0");
        assertThat(s).contains("\"intChange\":1");

        assertThat(s).doesNotContain("\"integerNull\":");
        assertThat(s).contains("\"integerDefault\":0");
        assertThat(s).contains("\"integerChange\":1");

        assertThat(s).contains("\"boolNull\":false");
        assertThat(s).contains("\"boolDefaultTrue\":true");
        assertThat(s).contains("\"boolChangeTrue\":false");
        assertThat(s).contains("\"boolDefaultFalse\":false");
        assertThat(s).contains("\"boolChangeTrue\":false");

        assertThat(s).doesNotContain("\"booleanNull\":");
        assertThat(s).contains("\"booleanDefaultTrue\":true");
        assertThat(s).contains("\"booleanChangeTrue\":false");
        assertThat(s).contains("\"booleanDefaultFalse\":false");
        assertThat(s).contains("\"booleanChangeTrue\":false");

        assertThat(s).doesNotContain("\"stringNull\":");
        assertThat(s).contains("\"stringDefault\":\"bla\"");
        assertThat(s).contains("\"stringChange\":\"foo\"");

        assertThat(s).contains("\"duration\":\"PT5M\"");
        assertThat(s).contains("\"zonedDateTime\":\"2013-09-08T16:19:12+02:00\"");
    }

    // Plugin polymorphism relies on PluginModule being registered on this mapper: without it a task would
    // deserialize as its abstract type and fail.
    @Test
    void shouldResolvePluginTypeThroughTheRegistry() throws JsonProcessingException {
        String json = JacksonMapper.ofJson().writeValueAsString(
            Log.builder().id("log").type(Log.class.getName()).message("hello").build()
        );

        Task task = objectMapper.readValue(json, Task.class);

        assertThat(task).isInstanceOf(Log.class);
        assertThat(task.getId()).isEqualTo("log");
    }

    // FlowInterface carries @JsonDeserialize(as = GenericFlow.class) while GenericFlow carries a bare
    // @JsonDeserialize whose only job is to mask it, otherwise the redirection recurses into itself. That
    // masking has to survive Micronaut 5's Jackson 2 annotation compatibility layer.
    @Test
    void shouldDeserializeFlowInterfaceAsGenericFlowWithoutRecursing() throws JsonProcessingException {
        FlowInterface flow = objectMapper.readValue(flowJson(), FlowInterface.class);

        assertThat(flow).isInstanceOf(GenericFlow.class);
        assertThat(flow.getId()).isEqualTo("a-flow");
    }

    // AbstractFlow carries the same bare @JsonDeserialize so FlowInterface's does not leak onto Flow, which
    // is not assignable to GenericFlow — un-masking it fails with "GenericFlow not subtype of Flow".
    @Test
    void shouldDeserializeConcreteFlowWithoutRedirectingItToGenericFlow() throws JsonProcessingException {
        Flow flow = objectMapper.readValue(flowJson(), Flow.class);

        assertThat(flow.getId()).isEqualTo("a-flow");
        assertThat(flow.getTasks()).hasSize(1);
    }

    // TenantSerializer is a ValueSerializerModifier bean that Micronaut injects with no explicit
    // registration, so nothing would fail if it stopped applying — tenantId would just start leaking.
    @Test
    void shouldNotSerializeTenantIdOfATenantInterface() {
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId("a-tenant")
            .namespace("io.kestra.tests")
            .flowId("a-flow")
            .state(new State())
            .build();

        String json = objectMapper.writeValueAsString(execution);

        assertThat(json).doesNotContain("tenantId");
        assertThat(json).doesNotContain("a-tenant");
        assertThat(json).contains("\"flowId\":\"a-flow\"");
    }

    // AbstractGraph uses @JsonTypeInfo(Id.CLASS) and none of its subclasses has a no-arg constructor, so each
    // concrete one needs a creator now that the Jackson bean-introspection module is gone. GraphTask got an
    // explicit @JsonCreator; these two are its siblings, reached whenever a FlowGraph carries a subflow or a
    // trigger node.
    @Test
    void shouldDeserializeEveryConcreteGraphNode() throws JsonProcessingException {
        AbstractGraph subflowNode = new SubflowGraphTask(
            Subflow.builder().id("subflow").type(Subflow.class.getName()).namespace("io.kestra.tests").flowId("sub").build(),
            null,
            List.of(),
            RelationType.SEQUENTIAL
        );
        AbstractGraph triggerNode = new GraphTrigger(
            Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").build(),
            null
        );

        for (AbstractGraph node : List.of(subflowNode, triggerNode)) {
            String json = JacksonMapper.ofJson().writeValueAsString(node);

            AbstractGraph read = objectMapper.readValue(json, AbstractGraph.class);

            assertThat(read).isInstanceOf(node.getClass());
        }
    }

    private static String flowJson() throws JsonProcessingException {
        return JacksonMapper.ofJson().writeValueAsString(
            Flow.builder()
                .id("a-flow")
                .namespace("io.kestra.tests")
                .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
                .build()
        );
    }

    @Test
    void deserialize() throws JacksonException {
        Bean bean = objectMapper.readValue(
            "{\"boolChangeFalse\":true,\"boolChangeTrue\":false,\"booleanChangeFalse\":true,\"booleanChangeTrue\":false,\"duration\":\"PT5M\",\"intChange\":1,\"integerChange\":1,\"stringChange\":\"foo\",\"zonedDateTime\":\"2013-09-08T16:19:12+02:00\"}",
            Bean.class
        );

        assertThat(bean.intNull).isZero();
        assertThat(bean.intDefault).isZero();
        assertThat(bean.intChange).isEqualTo(1);

        assertThat(bean.integerNull).isNull();
        assertThat(bean.integerDefault).isZero();
        assertThat(bean.integerChange).isEqualTo(1);

        assertThat(bean.boolNull).isFalse();
        assertThat(bean.boolDefaultTrue).isTrue();
        assertThat(bean.boolChangeTrue).isFalse();
        assertThat(bean.boolDefaultFalse).isFalse();
        assertThat(bean.boolChangeFalse).isTrue();

        assertThat(bean.booleanNull).isNull();
        assertThat(bean.booleanDefaultTrue).isTrue();
        assertThat(bean.booleanChangeTrue).isFalse();
        assertThat(bean.booleanDefaultFalse).isFalse();
        assertThat(bean.booleanChangeFalse).isTrue();

        assertThat(bean.stringNull).isNull();
        assertThat(bean.stringDefault).isEqualTo("bla");
        assertThat(bean.stringChange).isEqualTo("foo");

        assertThat(bean.duration).isEqualTo(Duration.parse("PT5M"));
    }
}
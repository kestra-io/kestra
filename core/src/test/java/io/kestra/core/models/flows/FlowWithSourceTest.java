package io.kestra.core.models.flows;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.types.VariableCondition;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.listeners.Listener;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.log.Log;
import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class FlowWithSourceTest {
    @Test
    void source() throws JsonProcessingException {
        FlowWithSource.FlowWithSourceBuilder<?, ?> builder = FlowWithSource.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .tasks(List.of(
                Return.builder()
                    .id(IdUtils.create())
                    .type(Return.class.getName())
                    .format("123456789 \n123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789\n" +
                        "123456789 \n" +
                        "123456789 \n" +
                        "123456789     \n")
                    .build()
            ));

        FlowWithSource flow = builder
            .source(JacksonMapper.ofYaml().writeValueAsString(builder.build().toFlow()))
            .build();

        String source = flow.getSource();

        assertThat(source, not(containsString("deleted: false")));
        assertThat(source, containsString("format: |\n"));
    }

    @Test
    void scalar() throws JsonProcessingException {
        FlowWithSource.FlowWithSourceBuilder<?, ?> builder = FlowWithSource.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .tasks(List.of(
                Log.builder()
                    .id(IdUtils.create())
                    .type(Log.class.getName())
                    .message("Hello World")
                    .build()
            ))
            .triggers(List.of(Schedule.builder().id("schedule").cron("0 1 9 * * *").build()));

        FlowWithSource flow = builder
            .source(JacksonMapper.ofYaml().writeValueAsString(builder.build().toFlow()))
            .build();

        String source = flow.getSource();

        assertThat(source, containsString("message: Hello World"));
        assertThat(source, containsString("  cron: 0 1 9 * * *"));
    }

    @Test
    void of() {
        // test that all fields are transmitted to FlowWithSource
        Flow flow = Flow.builder()
            .tenantId("tenantId")
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .description("description")
            .labels(List.of(
                new Label("key", "value")
            ))
            .inputs(List.of(
                StringInput.builder().name("strInput").build()
            ))
            .variables(Map.of(
                "varKey", "varValue"
            ))
            .tasks(List.of(
                Log.builder()
                    .id(IdUtils.create())
                    .type(Log.class.getName())
                    .message("Hello World")
                    .build()
            ))
            .errors(List.of(
                Log.builder()
                    .id(IdUtils.create())
                    .type(Log.class.getName())
                    .message("Error")
                    .build()
            ))
            .listeners(List.of(
                Listener.builder()
                    .conditions(List.of(VariableCondition.builder().expression("true").build()))
                    .build()
            ))
            .triggers(List.of(
                Schedule.builder().id("schedule").cron("0 1 9 * * *").build()
            ))
            .taskDefaults(List.of(
                TaskDefault.builder()
                    .type(Log.class.getName())
                    .forced(true)
                    .values(Map.of(
                        "message", "Default message"
                    ))
                    .build()
            ))
            .concurrency(
                Concurrency.builder()
                    .behavior(Concurrency.Behavior.CANCEL)
                    .limit(2)
                    .build()
            )
            .build();
        String expectedSource = flow.generateSource() + " # additional comment";
        FlowWithSource of = FlowWithSource.of(flow, expectedSource);

        assertThat(of.equalsWithoutRevision(flow), is(true));
        assertThat(of.getSource(), is(expectedSource));
    }
}
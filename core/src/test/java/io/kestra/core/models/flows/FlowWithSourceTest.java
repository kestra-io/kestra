package io.kestra.core.models.flows;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.scripts.Bash;
import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                Bash.builder()
                    .id(IdUtils.create())
                    .type(Return.class.getName())
                    .commands(new String[]{"timeout 1 bash -c 'cat < /dev/null > /dev/tcp/{{ inputs.host }}/{{ inputs.port }}'", "echo $?"})
                    .build()
            ))
            .triggers(List.of(Schedule.builder().id("schedule").cron("0 1 9 * * *").build()));

        FlowWithSource flow = builder
            .source(JacksonMapper.ofYaml().writeValueAsString(builder.build().toFlow()))
            .build();

        String source = flow.getSource();

        assertThat(source, containsString("  - \"timeout "));
        assertThat(source, containsString("  - echo"));
        assertThat(source, containsString("  cron: 0 1 9 * * *"));
    }
}
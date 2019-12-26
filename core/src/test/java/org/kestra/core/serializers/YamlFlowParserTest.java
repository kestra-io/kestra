package org.kestra.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kestra.core.exceptions.InvalidFlowException;
import org.kestra.core.models.tasks.retrys.Constant;
import org.junit.jupiter.api.Test;
import org.kestra.core.Utils;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.tasks.Task;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlFlowParserTest {
    private static ObjectMapper mapper = JacksonMapper.ofJson();

    @Test
    void parse() throws IOException {
        Flow flow = Utils.parse("flows/valids/full.yaml");

        assertThat(flow.getId(), is("full"));
        assertThat(flow.getTasks().size(), is(5));

        // third with all optionals
        Task optionals = flow.getTasks().get(2);
        assertThat(optionals.getTimeout(), is(1000));
        assertThat(optionals.getRetry().getType(), is("constant"));
        assertThat(optionals.getRetry().getMaxAttempt(), is(5));
        assertThat(((Constant) optionals.getRetry()).getInterval().getSeconds(), is(900L));
    }

    @Test
    void validation() throws IOException {
        assertThrows(InvalidFlowException.class, () -> {
            Utils.parse("flows/invalids/invalid.yaml");
        });

        try {
            Utils.parse("flows/invalids/invalid.yaml");
        } catch (InvalidFlowException e) {
            assertThat(e.getViolations().size(), is(3));
        }
    }

    @Test
    void serialization() throws IOException {
        Flow flow = Utils.parse("flows/valids/minimal.yaml");

        String s = mapper.writeValueAsString(flow);
        assertThat(s, is("{\"id\":\"minimal\",\"namespace\":\"org.kestra.tests\",\"tasks\":[{\"id\":\"date\",\"type\":\"org.kestra.core.tasks.debugs.Return\",\"format\":\"{{taskrun.startDate}}\"}]}"));
    }
}
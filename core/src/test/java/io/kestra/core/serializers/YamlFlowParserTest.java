package io.kestra.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.Input;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.TestsUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class YamlFlowParserTest {
    private static ObjectMapper mapper = JacksonMapper.ofJson();

    @Inject
    private YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Test
    void parse() {
        Flow flow = parse("flows/valids/full.yaml");

        assertThat(flow.getId(), is("full"));
        assertThat(flow.getTasks().size(), is(5));

        // third with all optionals
        Task optionals = flow.getTasks().get(2);
        assertThat(optionals.getTimeout(), is(Duration.ofMinutes(60)));
        assertThat(optionals.getRetry().getType(), is("constant"));
        assertThat(optionals.getRetry().getMaxAttempt(), is(5));
        assertThat(((Constant) optionals.getRetry()).getInterval().getSeconds(), is(900L));
    }

    @Test
    void allFlowable() {
        Flow flow = this.parse("flows/valids/all-flowable.yaml");

        assertThat(flow.getId(), is("all-flowable"));
        assertThat(flow.getTasks().size(), is(4));
    }

    @Test
    void validation() {
        assertThrows(ConstraintViolationException.class, () -> {
            this.parse("flows/invalids/invalid.yaml");
        });

        try {
            this.parse("flows/invalids/invalid.yaml");
        } catch (ConstraintViolationException e) {
            assertThat(e.getConstraintViolations().size(), is(4));
        }
    }

    @Test
    void empty() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/empty.yaml")
        );

        assertThat(exception.getConstraintViolations().size(), is(1));
        assertThat(new ArrayList<>(exception.getConstraintViolations()).get(0).getMessage(), is("must not be empty"));
    }

    @Test
    void inputsFailed() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/inputs.yaml")
        );

        assertThat(exception.getConstraintViolations().size(), is(2));
        assertThat(exception.getConstraintViolations().stream().filter(r -> r.getPropertyPath().toString().equals("inputs[0].name")).findFirst().orElseThrow().getMessage(), containsString("must match"));
        assertThat(exception.getConstraintViolations().stream().filter(r -> r.getPropertyPath().toString().equals("inputs[0].type")).findFirst().orElseThrow().getMessage(), is("must not be null"));
    }

    @Test
    void inputs() {
        Flow flow = this.parse("flows/valids/inputs.yaml");

        assertThat(flow.getInputs().size(), is(8));
        assertThat(flow.getInputs().stream().filter(Input::getRequired).count(), is(6L));
        assertThat(flow.getInputs().stream().filter(r -> !r.getRequired()).count(), is(2L));
        assertThat(flow.getInputs().stream().filter(r -> r.getDefaults() != null).count(), is(1L));
    }

    @Test
    void inputsBadType() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/inputs-bad-type.yaml")
        );

        assertThat(exception.getMessage(), containsString("not one of the values accepted for Enum class"));
    }

    @Test
    void listeners() {
         ConstraintViolationException exception = assertThrows(
             ConstraintViolationException.class,
             () -> this.parse("flows/invalids/listener.yaml")
         );

         assertThat(exception.getConstraintViolations().size(), is(2));
         assertThat(new ArrayList<>(exception.getConstraintViolations()).get(0).getMessage(), containsString("must not be empty"));
         assertThat(new ArrayList<>(exception.getConstraintViolations()).get(1).getMessage(), is("must not be empty"));
    }

    @Test
    void serialization() throws IOException {
        Flow flow = this.parse("flows/valids/minimal.yaml");

        String s = mapper.writeValueAsString(flow);
        assertThat(s, is("{\"id\":\"minimal\",\"namespace\":\"io.kestra.tests\",\"revision\":2,\"tasks\":[{\"id\":\"date\",\"type\":\"io.kestra.core.tasks.debugs.Return\",\"format\":\"{{taskrun.startDate}}\"}],\"deleted\":false}"));
    }

    @Test
    void noDefault() throws IOException {
        Flow flow = this.parse("flows/valids/parallel.yaml");

        String s = mapper.writeValueAsString(flow);
        assertThat(s, not(containsString("\"-c\"")));
        assertThat(s, containsString("\"deleted\":false"));
    }

    @Test
    void include() {
        Flow flow = parse("flows/helpers/include.yaml");

        assertThat(flow.getId(), is("include"));
        assertThat(flow.getTasks().size(), is(2));

        assertThat(((Return) flow.getTasks().get(0)).getFormat(), containsString("Lorem Ipsum"));
        assertThat(((Return) flow.getTasks().get(0)).getFormat(), containsString("\n"));
        assertThat(((Return) flow.getTasks().get(1)).getFormat(), containsString("Lorem Ipsum"));
        assertThat(((Return) flow.getTasks().get(1)).getFormat(), containsString("\n"));
    }

    @Test
    void trigger() {
        Flow parse = this.parse("flows/tests/trigger.yaml");
        assertThat(((Schedule) parse.getTriggers().get(0)).getBackfill().getStart(), is(ZonedDateTime.parse("2020-01-01T00:00:00+02:00")));
    }

    @Test
    void triggerEmpty() {
        Flow parse = this.parse("flows/tests/trigger-empty.yaml");
        assertThat(((Schedule) parse.getTriggers().get(0)).getBackfill().getStart(), nullValue());
    }

    @Test
    void includeFailed() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/helpers/include-failed.yaml")
        );

        assertThat(exception.getConstraintViolations().size(), is(1));
        assertThat(new ArrayList<>(exception.getConstraintViolations()).get(0).getMessage(), containsString("File not found at location"));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file);
    }
}

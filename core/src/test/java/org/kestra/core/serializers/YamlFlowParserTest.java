package org.kestra.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.retrys.Constant;
import org.kestra.core.utils.TestsUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class YamlFlowParserTest {
    private static ObjectMapper mapper = JacksonMapper.ofJson();

    @Inject
    private YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Test
    void parse() throws IOException {
        Flow flow = parse("flows/valids/full.yaml");

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
    void inputs() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/inputs.yaml")
        );

        assertThat(exception.getConstraintViolations().size(), is(2));
        assertThat(new ArrayList<>(exception.getConstraintViolations()).get(0).getMessage(), containsString("must match"));
        assertThat(new ArrayList<>(exception.getConstraintViolations()).get(1).getMessage(), is("must not be null"));
    }

    @Test
    void inputsBadType() {
        InvalidFormatException exception = assertThrows(
            InvalidFormatException.class,
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
        assertThat(s, is("{\"id\":\"minimal\",\"namespace\":\"org.kestra.tests\",\"revision\":2,\"tasks\":[{\"id\":\"date\",\"type\":\"org.kestra.core.tasks.debugs.Return\",\"format\":\"{{taskrun.startDate}}\"}]}"));
    }

    private Flow parse(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file);
    }
}

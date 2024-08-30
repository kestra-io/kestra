package io.kestra.core.serializers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class YamlFlowParserTest {
    private static ObjectMapper mapper = JacksonMapper.ofJson();

    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private ModelValidator modelValidator;

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
    void parseString() throws IOException {
        Flow flow = parseString("flows/valids/full.yaml");

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
            modelValidator.validate(this.parse("flows/invalids/invalid.yaml"));
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
            () -> modelValidator.validate(this.parse("flows/invalids/empty.yaml"))
        );

        assertThat(exception.getConstraintViolations().size(), is(1));
        assertThat(new ArrayList<>(exception.getConstraintViolations()).getFirst().getMessage(), is("must not be empty"));
    }

    @Test
    void inputsFailed() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> modelValidator.validate(this.parse("flows/invalids/inputs.yaml"))
        );

        assertThat(exception.getConstraintViolations().size(), is(2));
        exception.getConstraintViolations().forEach(
            c -> assertThat(c.getMessage(), anyOf(
                is("Invalid type: null"),
                containsString("missing type id property 'type' (for POJO property 'inputs')"))
            )
        );
    }

    @Test
    void inputs() {
        Flow flow = this.parse("flows/valids/inputs.yaml");

        assertThat(flow.getInputs().size(), is(28));
        assertThat(flow.getInputs().stream().filter(Input::getRequired).count(), is(10L));
        assertThat(flow.getInputs().stream().filter(r -> !r.getRequired()).count(), is(18L));
        assertThat(flow.getInputs().stream().filter(r -> r.getDefaults() != null).count(), is(2L));
        assertThat(flow.getInputs().stream().filter(r -> r instanceof StringInput && ((StringInput)r).getValidator() != null).count(), is(1L));
    }


    @Test
    void inputsOld() {
        Flow flow = this.parse("flows/tests/inputs-old.yaml");

        assertThat(flow.getInputs().size(), is(1));
        assertThat(flow.getInputs().getFirst().getId(), is("myInput"));
        assertThat(flow.getInputs().getFirst().getType(), is(Type.STRING));
    }

    @Test
    void inputsBadType() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/inputs-bad-type.yaml")
        );

        assertThat(exception.getMessage(), containsString("Invalid type: FOO"));
    }

    @Test
    void listeners() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> modelValidator.validate(this.parse("flows/invalids/listener.yaml"))
        );

        assertThat(exception.getConstraintViolations().size(), is(2));
        assertThat(new ArrayList<>(exception.getConstraintViolations()).getFirst().getMessage(), containsString("must not be empty"));
        assertThat(new ArrayList<>(exception.getConstraintViolations()).get(1).getMessage(), is("must not be empty"));
    }

    @Test
    void serialization() throws IOException {
        Flow flow = this.parse("flows/valids/minimal.yaml");

        String s = mapper.writeValueAsString(flow);
        assertThat(s, is("{\"id\":\"minimal\",\"namespace\":\"io.kestra.tests\",\"revision\":2,\"disabled\":false,\"deleted\":false,\"tasks\":[{\"id\":\"date\",\"type\":\"io.kestra.plugin.core.debug.Return\",\"format\":\"{{taskrun.startDate}}\"}]}"));
    }

    @Test
    void noDefault() throws IOException {
        Flow flow = this.parse("flows/valids/parallel.yaml");

        String s = mapper.writeValueAsString(flow);
        assertThat(s, not(containsString("\"-c\"")));
        assertThat(s, containsString("\"deleted\":false"));
    }

    @Test
    void invalidTask() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/invalid-task.yaml")
        );

        assertThat(exception.getConstraintViolations().size(), is(2));
        assertThat(exception.getConstraintViolations().stream().filter(e -> e.getMessage().contains("Invalid type")).findFirst().orElseThrow().getMessage(), containsString("Invalid type: io.kestra.plugin.core.debug.MissingOne"));
    }

    @Test
    void invalidProperty() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/invalid-property.yaml")
        );

        assertThat(exception.getMessage(), is("Unrecognized field \"invalid\" (class io.kestra.plugin.core.debug.Return), not marked as ignorable (11 known properties: \"logLevel\", \"timeout\", \"format\", \"retry\", \"type\", \"id\", \"description\", \"workerGroup\", \"logToFile\", \"disabled\", \"allowFailure\"])"));
        assertThat(exception.getConstraintViolations().size(), is(1));
        assertThat(exception.getConstraintViolations().iterator().next().getPropertyPath().toString(), is("io.kestra.core.models.flows.Flow[\"tasks\"]->java.util.ArrayList[0]->io.kestra.plugin.core.debug.Return[\"invalid\"]"));
    }

    @Test
    void invalidPropertyOk() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/invalids/invalid-property.yaml");
        assert resource != null;

        File file = new File(resource.getFile());
        String flowSource = Files.readString(file.toPath(), Charset.defaultCharset());
        TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {};
        Map<String, Object> flow = JacksonMapper.ofYaml().readValue(flowSource, TYPE_REFERENCE);

        Flow parse = yamlFlowParser.parse(flow, Flow.class, false);

        assertThat(parse.getId(), is("duplicate"));
    }

    @Test
    void invalidParallel() {
        Flow parse = this.parse("flows/invalids/invalid-parallel.yaml");
        Optional<ConstraintViolationException> valid = modelValidator.isValid(parse);

        assertThat(valid.isPresent(), is(true));
        assertThat(valid.get().getConstraintViolations().size(), is(8));
        assertThat(new ArrayList<>(valid.get().getConstraintViolations()).stream().filter(r -> r.getMessage().contains("must not be empty")).count(), is(3L));
    }

    @Test
    void duplicateKey() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/duplicate-key.yaml")
        );

        assertThat(exception.getConstraintViolations().size(), is(1));
        assertThat(new ArrayList<>(exception.getConstraintViolations()).getFirst().getMessage(), containsString("Duplicate field 'variables.tf'"));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file, Flow.class);
    }

    private Flow parseString(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        String input = Files.readString(Path.of(resource.getPath()), Charset.defaultCharset());

        return yamlFlowParser.parse(input, Flow.class);
    }
}

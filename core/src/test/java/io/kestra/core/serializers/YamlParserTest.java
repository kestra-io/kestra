package io.kestra.core.serializers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
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
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class YamlParserTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofYaml().copy();

    @Inject
    private ModelValidator modelValidator;

    @Test
    void parse() {
        Flow flow = parse("flows/valids/full.yaml");

        assertThat(flow.getId()).isEqualTo("full");
        assertThat(flow.getTasks().size()).isEqualTo(5);

        // third with all optionals
        Task optionals = flow.getTasks().get(2);
        assertThat(optionals.getTimeout()).isEqualTo(Property.builder().expression("PT60M").build());
        assertThat(optionals.getRetry().getType()).isEqualTo("constant");
        assertThat(optionals.getRetry().getMaxAttempts()).isEqualTo(5);
        assertThat(((Constant) optionals.getRetry()).getInterval().getSeconds()).isEqualTo(900L);
    }


    @Test
    void parseString() throws IOException {
        Flow flow = parseString("flows/valids/full.yaml");

        assertThat(flow.getId()).isEqualTo("full");
        assertThat(flow.getTasks().size()).isEqualTo(5);

        // third with all optionals
        Task optionals = flow.getTasks().get(2);
        assertThat(optionals.getTimeout()).isEqualTo(Property.builder().expression("PT60M").build());
        assertThat(optionals.getRetry().getType()).isEqualTo("constant");
        assertThat(optionals.getRetry().getMaxAttempts()).isEqualTo(5);
        assertThat(((Constant) optionals.getRetry()).getInterval().getSeconds()).isEqualTo(900L);
    }

    @Test
    void allFlowable() {
        Flow flow = this.parse("flows/valids/all-flowable.yaml");

        assertThat(flow.getId()).isEqualTo("all-flowable");
        assertThat(flow.getTasks().size()).isEqualTo(4);
    }

    @Test
    void validation() {
        assertThrows(ConstraintViolationException.class, () -> {
            modelValidator.validate(this.parse("flows/invalids/invalid.yaml"));
        });

        try {
            this.parse("flows/invalids/invalid.yaml");
        } catch (ConstraintViolationException e) {
            assertThat(e.getConstraintViolations().size()).isEqualTo(4);
        }
    }

    @Test
    void empty() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> modelValidator.validate(this.parse("flows/invalids/empty.yaml"))
        );

        assertThat(exception.getConstraintViolations().size()).isEqualTo(1);
        assertThat(new ArrayList<>(exception.getConstraintViolations()).getFirst().getMessage()).isEqualTo("must not be empty");
    }

    @Test
    void inputsFailed() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> modelValidator.validate(this.parse("flows/invalids/inputs.yaml"))
        );

        assertThat(exception.getConstraintViolations().size()).isEqualTo(2);
        exception.getConstraintViolations().forEach(
            c -> assertThat(c.getMessage())
                .satisfiesAnyOf(
                    arg -> assertThat(arg).isEqualTo("Invalid type: null"),
                    arg -> assertThat(arg).contains("missing type id property 'type' (for POJO property 'inputs')")
                )
        );
    }

    @Test
    void inputs() {
        Flow flow = this.parse("flows/valids/inputs.yaml");

        assertThat(flow.getInputs().size()).isEqualTo(31);
        assertThat(flow.getInputs().stream().filter(Input::getRequired).count()).isEqualTo(12L);
        assertThat(flow.getInputs().stream().filter(r -> !r.getRequired()).count()).isEqualTo(19L);
        assertThat(flow.getInputs().stream().filter(r -> r.getDefaults() != null).count()).isEqualTo(4L);
        assertThat(flow.getInputs().stream().filter(r -> r instanceof StringInput stringInput && stringInput.getValidator() != null).count()).isEqualTo(1L);
    }


    @Test
    void inputsOld() {
        Flow flow = this.parse("flows/tests/inputs-old.yaml");

        assertThat(flow.getInputs().size()).isEqualTo(1);
        assertThat(flow.getInputs().getFirst().getId()).isEqualTo("myInput");
        assertThat(flow.getInputs().getFirst().getType()).isEqualTo(Type.STRING);
    }

    @Test
    void inputsBadType() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/inputs-bad-type.yaml")
        );

        assertThat(exception.getMessage()).contains("Invalid type: FOO");
    }

    @Test
    void listeners() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> modelValidator.validate(this.parse("flows/invalids/listener.yaml"))
        );

        assertThat(exception.getConstraintViolations().size()).isEqualTo(2);
        assertThat(new ArrayList<>(exception.getConstraintViolations()).getFirst().getMessage()).contains("must not be empty");
        assertThat(new ArrayList<>(exception.getConstraintViolations()).get(1).getMessage()).isEqualTo("must not be empty");
    }

    @Test
    void serialization() throws IOException {
        Flow flow = this.parse("flows/valids/minimal.yaml");

        String s = MAPPER.writeValueAsString(flow);
        assertThat(s).isEqualTo("{\"id\":\"minimal\",\"namespace\":\"io.kestra.tests\",\"revision\":2,\"disabled\":false,\"deleted\":false,\"labels\":[{\"key\":\"system.readOnly\",\"value\":\"true\"},{\"key\":\"existing\",\"value\":\"label\"}],\"tasks\":[{\"id\":\"date\",\"type\":\"io.kestra.plugin.core.debug.Return\",\"format\":\"{{taskrun.startDate}}\"}]}");
    }

    @Test
    void noDefault() throws IOException {
        Flow flow = this.parse("flows/valids/parallel.yaml");

        String s = MAPPER.writeValueAsString(flow);
        assertThat(s).doesNotContain("\"-c\"");
        assertThat(s).contains("\"deleted\":false");
    }

    @Test
    void invalidTask() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/invalid-task.yaml")
        );

        assertThat(exception.getConstraintViolations().size()).isEqualTo(2);
        assertThat(exception.getConstraintViolations().stream().filter(e -> e.getMessage().contains("Invalid type")).findFirst().orElseThrow().getMessage()).contains("Invalid type: io.kestra.plugin.core.debug.MissingOne");
    }

    @Test
    void invalidProperty() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/invalid-property.yaml")
        );

        assertThat(exception.getMessage()).startsWith("Unrecognized field \"invalid\" (class io.kestra.plugin.core.debug.Return), not marked as ignorable");
        assertThat(exception.getConstraintViolations().size()).isEqualTo(1);
        assertThat(exception.getConstraintViolations().iterator().next().getPropertyPath().toString()).isEqualTo("io.kestra.core.models.flows.Flow[\"tasks\"]->java.util.ArrayList[0]->io.kestra.plugin.core.debug.Return[\"invalid\"]");
    }

    @Test
    void invalidPropertyOk() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/invalids/invalid-property.yaml");
        assert resource != null;

        File file = new File(resource.getFile());
        String flowSource = Files.readString(file.toPath(), Charset.defaultCharset());
        TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {};
        Map<String, Object> flow = JacksonMapper.ofYaml().readValue(flowSource, TYPE_REFERENCE);

        Flow parse = YamlParser.parse(flow, Flow.class, false);

        assertThat(parse.getId()).isEqualTo("duplicate");
    }

    @Test
    void invalidParallel() {
        Flow parse = this.parse("flows/invalids/invalid-parallel.yaml");
        Optional<ConstraintViolationException> valid = modelValidator.isValid(parse);

        assertThat(valid.isPresent()).isTrue();
        assertThat(valid.get().getConstraintViolations().size()).isEqualTo(10);
        assertThat(new ArrayList<>(valid.get().getConstraintViolations()).stream().filter(r -> r.getMessage().contains("must not be empty")).count()).isEqualTo(3L);
    }

    @Test
    void duplicateKey() {
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> this.parse("flows/invalids/duplicate-key.yaml")
        );

        assertThat(exception.getConstraintViolations().size()).isEqualTo(1);
        assertThat(new ArrayList<>(exception.getConstraintViolations()).getFirst().getMessage()).contains("Duplicate field 'variables.tf'");
    }

    @Test
    void vaildLabelsParser() throws IOException {
        Flow flow = parse("flows/valids/labels-deserialization.yaml");
        // like change execution state api,Serialize flow to YAML/JSON string
        String s = OBJECT_MAPPER.writeValueAsString(flow);
        assertThat(s).isEqualTo("id: labels-deserialization\n" +
            "namespace: io.kestra.tests\n" +
            "disabled: false\n" +
            "deleted: false\n" +
            "labels:\n" +
            "- key: key1\n" +
            "  value: 123\n" +
            "tasks:\n" +
            "- id: t1\n" +
            "  type: io.kestra.plugin.core.log.Log\n" +
            "  message: \"{{ task.id }}\"\n");
        Map<String, Object> mapFlow = OBJECT_MAPPER.readValue(s, JacksonMapper.MAP_TYPE_REFERENCE);
        // Parse into FlowWithSource (simulates state update scenario)
        FlowWithSource parse = YamlParser.parse(mapFlow, FlowWithSource.class, false);

        assertThat(parse.getLabels().size()).isEqualTo(1);;
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(file, Flow.class);
    }

    private Flow parseString(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        String input = Files.readString(Path.of(resource.getPath()), Charset.defaultCharset());

        return YamlParser.parse(input, Flow.class);
    }
}

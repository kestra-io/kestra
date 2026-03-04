package io.kestra.core.runners.pebble.functions;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class YamlFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void fromString() throws IllegalVariableEvaluationException {

        ImmutableMap<String, Object> runContext = ImmutableMap.of(
            "yaml",
            """
                # comment
                string: string
                int: 123
                bool: true
                float: 1.23
                instant: "1918-02-24T00:00:00Z"
                date: "1991-08-20"
                time: "23:59:59"
                duration: "PT5M6S"
                'null':
                object:
                  key: "value"
                  child:
                    key: "value"
                array:
                  - string
                reference: &ref
                    key: reference
                default: *ref
                """
        );

        String render;
        render = variableRenderer.render("{{ yaml(yaml).string }}", runContext);
        assertThat(render).isEqualTo("string");

        render = variableRenderer.render("{{ yaml(yaml).int }}", runContext);
        assertThat(render).isEqualTo("123");

        render = variableRenderer.render("{{ yaml(yaml).bool }}", runContext);
        assertThat(render).isEqualTo("true");

        render = variableRenderer.render("{{ yaml(yaml).float }}", runContext);
        assertThat(render).isEqualTo("1.23");

        render = variableRenderer.render("{{ yaml(yaml).instant }}", runContext);
        assertThat(render).isEqualTo("1918-02-24T00:00:00Z");

        render = variableRenderer.render("{{ yaml(yaml).date }}", runContext);
        assertThat(render).isEqualTo("1991-08-20");

        render = variableRenderer.render("{{ yaml(yaml).time }}", runContext);
        assertThat(render).isEqualTo("23:59:59");

        // Kestra internally does not handle null values in objects
        // render = variableRenderer.render("{{ yaml(yaml).null ?? '' }}", runContext);
        // assertThat(render, is(""));

        render = variableRenderer.render("{{ yaml(yaml).object.child.key }}", runContext);
        assertThat(render).isEqualTo("value");

        render = variableRenderer.render("{{ yaml(yaml).array[0] }}", runContext);
        assertThat(render).isEqualTo("string");

        // as of 2024-03-15 Jackson YAML does not support anchors
        // https://github.com/FasterXML/jackson-dataformats-text/issues/98
        // render = variableRenderer.render("{{ yaml(yaml).default.key }}", runContext);
        // assertThat(render, is("reference"));

    }
}

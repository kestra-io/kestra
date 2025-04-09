package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class IndentFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void indentNull() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ null | indent(2) }}", Map.of());
        assertThat(render).isNullOrEmpty();
    }

    @Test
    void indentEmpty() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ '' | indent(2) }}", Map.of());
        assertThat(render).isEqualTo("");
    }

    @Test
    void indentEmptyLines() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"\n\n\" | indent(2) }}", Map.of());
        assertThat(render).isEqualTo("\n  \n  ");
    }

    @Test
    void indentString() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'string' | indent(2) }}", Map.of());
        assertThat(render).isEqualTo("string");
    }

    @Test
    void indentInteger() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 1 | indent(2) }}", Map.of());
        assertThat(render).isEqualTo("1");
    }

    @Test
    void indentStringWithCRLF() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\r\nsecond line\" | indent(2) }}", Map.of());
        assertThat(render).isEqualTo("first line\r\n  second line");
    }

    @Test
    void indentStringWithLF() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\nsecond line\" | indent(2) }}", Map.of());
        assertThat(render).isEqualTo("first line\n  second line");
    }

    @Test
    void indentStringWithCR() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\rsecond line\" | indent(2) }}", Map.of());
        assertThat(render).isEqualTo("first line\r  second line");
    }

    @Test
    void indentStringWithSystemNewLine() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line"+System.lineSeparator()+"second line\" | indent(2) }}", Map.of());
        assertThat(render).isEqualTo("first line" + System.lineSeparator() + "  second line");
    }

    @Test
    void indentWithTab() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\nsecond line\" | indent(2, \"\t\") }}", Map.of());
        assertThat(render).isEqualTo("first line\n\t\tsecond line");
    }

}

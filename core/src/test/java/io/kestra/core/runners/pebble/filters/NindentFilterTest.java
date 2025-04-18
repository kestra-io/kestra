package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class NindentFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void nindentNull() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ null | nindent(2) }}", Map.of());
        assertThat(render).isNullOrEmpty();
    }

    @Test
    void nindentEmpty() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ '' | nindent(2) }}", Map.of());
        assertThat(render).isEqualTo("");
    }

    @Test
    void nindentEmptyLines() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"\n\n\" | nindent(2) }}", Map.of());
        assertThat(render).isEqualTo("\n  \n  \n  ");
    }

    @Test
    void nindentString() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'string' | nindent(2) }}", Map.of());
        assertThat(render).isEqualTo("\n  string");
    }

    @Test
    void nindentInteger() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 1 | nindent(2) }}", Map.of());
        assertThat(render).isEqualTo("\n  1");
    }

    @Test
    void nindentStringWithCRLF() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\r\nsecond line\" | nindent(2) }}", Map.of());
        assertThat(render).isEqualTo("\r\n  first line\r\n  second line");
    }

    @Test
    void nindentStringWithLF() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\nsecond line\" | nindent(2) }}", Map.of());
        assertThat(render).isEqualTo("\n  first line\n  second line");
    }

    @Test
    void nindentStringWithCR() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\rsecond line\" | nindent(2) }}", Map.of());
        assertThat(render).isEqualTo("\r  first line\r  second line");
    }

    @Test
    void nindentStringWithSystemNewLine() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line"+System.lineSeparator()+"second line\" | nindent(2) }}", Map.of());
        assertThat(render).isEqualTo(System.lineSeparator() + "  first line" + System.lineSeparator() + "  second line");
    }

    @Test
    void nindentWithTab() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\nsecond line\" | nindent(2, \"\t\") }}", Map.of());
        assertThat(render).isEqualTo("\n\t\tfirst line\n\t\tsecond line");
    }

}

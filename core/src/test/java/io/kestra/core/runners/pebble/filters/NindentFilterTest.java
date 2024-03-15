package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class NindentFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void nindentNull() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ null | nindent(2) }}", Map.of());
        assertThat(render, emptyOrNullString());
    }

    @Test
    void nindentEmpty() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ '' | nindent(2) }}", Map.of());
        assertThat(render, is(""));
    }

    @Test
    void nindentEmptyLines() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"\n\n\" | nindent(2) }}", Map.of());
        assertThat(render, is("\n  \n  \n  "));
    }

    @Test
    void nindentString() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'string' | nindent(2) }}", Map.of());
        assertThat(render, is("\n  string"));
    }

    @Test
    void nindentInteger() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 1 | nindent(2) }}", Map.of());
        assertThat(render, is("\n  1"));
    }

    @Test
    void nindentStringWithCRLF() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\r\nsecond line\" | nindent(2) }}", Map.of());
        assertThat(render, is("\r\n  first line\r\n  second line"));
    }

    @Test
    void nindentStringWithLF() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\nsecond line\" | nindent(2) }}", Map.of());
        assertThat(render, is("\n  first line\n  second line"));
    }

    @Test
    void nindentStringWithCR() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\rsecond line\" | nindent(2) }}", Map.of());
        assertThat(render, is("\r  first line\r  second line"));
    }

    @Test
    void nindentStringWithSystemNewLine() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line"+System.lineSeparator()+"second line\" | nindent(2) }}", Map.of());
        assertThat(render, is(System.lineSeparator()+"  first line"+System.lineSeparator()+"  second line"));
    }

    @Test
    void nindentWithTab() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"first line\nsecond line\" | nindent(2, \"\t\") }}", Map.of());
        assertThat(render, is("\n\t\tfirst line\n\t\tsecond line"));
    }

}

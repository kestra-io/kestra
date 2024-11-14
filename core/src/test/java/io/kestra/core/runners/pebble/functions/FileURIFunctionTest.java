package io.kestra.core.runners.pebble.functions;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;

@KestraTest
public class FileURIFunctionTest {
    @Inject 
    VariableRenderer variableRenderer;

    @Test
    void fileURIFunction() throws IllegalVariableEvaluationException{
        String render = variableRenderer.render("{{ fileURI(fileA) }}", Map.of("fileA", "test"));
        assertThat(render, is("test"));
    }

}

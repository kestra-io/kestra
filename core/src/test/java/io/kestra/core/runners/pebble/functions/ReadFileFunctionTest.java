package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class ReadFileFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Inject
    StorageInterface storageInterface;

    @Test
    void readFile() throws IllegalVariableEvaluationException, IOException {
        String namespace = "io.kestra.tests";
        String filePath = "file.txt";
        storageInterface.createDirectory(null, URI.create(storageInterface.namespaceFilePrefix(namespace)));
        storageInterface.put(null, URI.create(storageInterface.namespaceFilePrefix(namespace) + "/" + filePath), new ByteArrayInputStream("Hello from {{ flow.namespace }}".getBytes()));

        String render = variableRenderer.render("{{ read('" + filePath + "') }}", Map.of("flow", Map.of("namespace", namespace)));
        assertThat(render, is("Hello from " + namespace));
    }

    @Test
    void readUnknownFile() {
        IllegalVariableEvaluationException illegalVariableEvaluationException = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ read('unknown.txt') }}", Map.of("flow", Map.of("namespace", "io.kestra.tests"))));
        assertThat(illegalVariableEvaluationException.getCause().getCause().getClass(), is(FileNotFoundException.class));
    }
}

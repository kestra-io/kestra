package io.kestra.core.runners.pebble.functions;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.net.URI;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class AbstractFileFunctionTest {

    @Inject
    ReadFileFunction readFileFunction;

    @Test
    void namespaceFromURI(){
    String namespace1 = readFileFunction.extractNamespace(URI.create("kestra:///demo/simple-write-oss/executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt"));
    assertThat(namespace1).isEqualTo("demo");

    String namespace2 = readFileFunction.extractNamespace(URI.create("kestra:///io/kestra/tests/simple-write-oss/executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt"));
    assertThat(namespace2).isEqualTo("io.kestra.tests");

    assertThrows(IllegalArgumentException.class, () ->readFileFunction.extractNamespace(URI.create("kestra:///simple-write-oss/executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt")));
    assertThrows(IllegalArgumentException.class, () ->readFileFunction.extractNamespace(URI.create("kestra:///executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt")));
    }
}

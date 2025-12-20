package io.kestra.core.runners.pebble.functions;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.net.URI;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@KestraTest
public class AbstractFileFunctionTest {

    @Inject
    ReadFileFunction readFileFunction;

    @Test
    void namespaceFromURI(){

    String namespace = readFileFunction.extractNamespace(URI.create("kestra:///demo/simple-write-oss/executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt"));
    assertThat(namespace).isEqualTo("demo");
    }
    @Test
    void dno(){

    }
}

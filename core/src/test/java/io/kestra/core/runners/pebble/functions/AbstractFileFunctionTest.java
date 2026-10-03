package io.kestra.core.runners.pebble.functions;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class AbstractFileFunctionTest {

    @Inject
    ReadFileFunction readFileFunction;

    @Test
    void namespaceFromURI() {
        String namespace1 = readFileFunction
            .extractNamespace(URI.create("kestra:///demo/simple-write-oss/executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt"));
        assertThat(namespace1).isEqualTo("demo");
        assertThat(
            readFileFunction.extractNamespace(
                URI.create(
                    "kestra://demo/simple-write-oss/executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt"
                )
            )
        ).isEqualTo(namespace1);

        String namespace2 = readFileFunction
            .extractNamespace(URI.create("kestra:///io/kestra/tests/simple-write-oss/executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt"));
        assertThat(namespace2).isEqualTo("io.kestra.tests");

        assertThrows(
            IllegalArgumentException.class,
            () -> readFileFunction.extractNamespace(URI.create("kestra:///simple-write-oss/executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt"))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> readFileFunction.extractNamespace(URI.create("kestra:///executions/4Tnd2zrWGoHGrufwyt738j/tasks/write/2FOeylkRr5tktwIQqFh56w/18316959863401460785.txt"))
        );
    }

    @Test
    void shouldRejectParentTraversalHiddenInTheAuthority() {
        URI escaped = URI.create("kestra://company%2Fteam%2Fflow%2Fexecutions%2FEXECID%2F..%2F..%2F..%2F..%2Fother/secret.txt");
        assertThat(readFileFunction.isFileUriValid("company.team", "flow", "EXECID", escaped)).isFalse();

        URI inside = URI.create("kestra://company/team/flow/executions/EXECID/tasks/write/out.txt");
        assertThat(readFileFunction.isFileUriValid("company.team", "flow", "EXECID", inside)).isTrue();

        URI dottedName = URI.create("kestra://company/team/flow/executions/EXECID/my..file.txt");
        assertThat(readFileFunction.isFileUriValid("company.team", "flow", "EXECID", dottedName)).isTrue();

        // This path matches the execution-file pattern, so namespace extraction would return
        // "company.team" and the namespace allow-list would accept it. The ".." segments must be rejected first.
        URI escapedThroughTasks = URI.create(
            "kestra://company/team/flow/executions/EXECID/tasks/run/../../../../../../../other/secret.txt"
        );
        assertThat(readFileFunction.isFileUriValid("company.team", "flow", "EXECID", escapedThroughTasks)).isFalse();
        assertThrows(IllegalArgumentException.class, () -> readFileFunction.extractNamespace(escapedThroughTasks));
    }
}

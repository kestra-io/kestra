package io.kestra.core.runners;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Property(name = "kestra.tasks.tmp-dir.path", value = "${java.io.tmpdir}/kestra-working-dir-test")
class WorkingDirFactoryTest {

    @Inject
    WorkingDirFactory workingDirFactory;

    @Test
    void shouldCreateWorkingDirGivenKestraTmpDir() {
        // Given
        WorkingDir workingDirectory = workingDirFactory.createWorkingDirectory();
        // When
        Path path = workingDirectory.path();
        // Then
        assertThat(path.getParent()).isEqualTo(Path.of(System.getProperty("java.io.tmpdir"), "kestra-working-dir-test"));
    }
}

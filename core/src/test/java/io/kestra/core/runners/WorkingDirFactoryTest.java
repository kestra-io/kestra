package io.kestra.core.runners;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
@Property(name = "kestra.tasks.tmp-dir.path", value = "/tmp/sub/dir/tmp/")
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
        assertThat(path.toFile().getAbsolutePath().startsWith("/tmp/sub/dir/tmp/"), is(true));
    }
}
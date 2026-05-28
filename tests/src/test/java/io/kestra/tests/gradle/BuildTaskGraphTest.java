package io.kestra.tests.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the assembleFrontend task is correctly wired in the Gradle task graph:
 * - included and ordered before webserver:processResources when building the final JAR
 * - excluded from the check/test graph to avoid a costly npm build on every compile cycle
 */
class BuildTaskGraphTest {

    private static final Path ROOT_PROJECT_DIR =
        Paths.get(System.getProperty("rootProjectDir"));

    @Test
    void shouldRunAssembleFrontendBeforeWebserverProcessResourcesWhenBuildingJar() {
        // Given / When
        BuildResult result = GradleRunner.create()
            .withProjectDir(ROOT_PROJECT_DIR.toFile())
            .withArguments("shadowJar", "--dry-run")
            .build();

        List<String> tasks = parseDryRunTasks(result.getOutput());

        // Then
        assertThat(tasks)
            .as("Both assembleFrontend and webserver:processResources must be in the shadowJar task graph")
            .contains(":ui:assembleFrontend", ":webserver:processResources");

        assertThat(tasks.indexOf(":ui:assembleFrontend"))
            .as("assembleFrontend must precede webserver:processResources — " +
                "the built UI files are written to webserver/src/main/resources/ui and must " +
                "be present when processResources copies them into the build output")
            .isLessThan(tasks.indexOf(":webserver:processResources"));
    }

    // Enable once https://github.com/kestra-io/kestra/pull/16304 is merged.
    // On develop, processResources uses dependsOn(':ui:assembleFrontend') which pulls
    // assembleFrontend into every compile/test task graph. The fix switches to mustRunAfter
    // so assembleFrontend is only triggered when shadowJar (or executableJar) is requested.
    @Disabled
    @Test
    void shouldNotRunAssembleFrontendWhenRunningCheck() {
        // Given / When
        BuildResult result = GradleRunner.create()
            .withProjectDir(ROOT_PROJECT_DIR.toFile())
            .withArguments(":webserver:check", "--dry-run")
            .build();

        // Then
        assertThat(result.getOutput())
            .as("assembleFrontend must not be triggered by check — " +
                "it would force an expensive npm build on every compile/test cycle")
            .doesNotContain(":ui:assembleFrontend");
    }

    /** Extracts ordered task names from Gradle --dry-run output (lines like ":task:name SKIPPED"). */
    private static List<String> parseDryRunTasks(String output) {
        return Arrays.stream(output.split("\n"))
            .map(String::trim)
            .filter(line -> line.startsWith(":") && line.endsWith("SKIPPED"))
            .map(line -> line.substring(0, line.lastIndexOf(' ')).trim())
            .collect(Collectors.toList());
    }
}

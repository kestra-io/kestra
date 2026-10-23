package io.kestra.plugin.scripts.runner.docker;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerTmpfsTest {
    private static final String WORKING_DIR = "/tmp/kestra-wd/tmp/abc";

    @Test
    void shouldNormalizePathsAndKeepOptions() {
        assertThat(Docker.tmpfsMounts(Map.of("/scratch/", " rw,noexec,size=512m ", "/cache", ""), WORKING_DIR, true))
            .containsExactlyInAnyOrderEntriesOf(Map.of("/scratch", "rw,noexec,size=512m", "/cache", ""));
    }

    @Test
    void shouldRejectRelativeOrRootPath() {
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("scratch", ""), WORKING_DIR, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be an absolute path");
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/", ""), WORKING_DIR, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("other than the root directory");
    }

    @Test
    void shouldRejectPathDeclaredTwiceOnceNormalized() {
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/scratch", "", "/scratch/", ""), WORKING_DIR, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The tmpfs mount path '/scratch' is declared more than once.");
    }

    @Test
    void shouldRejectOptionsDockerWouldRefuseAtStart() {
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/scratch", "rw,sizee=1m"), WORKING_DIR, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The tmpfs mount '/scratch' has an unsupported option 'sizee'.");
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/scratch", "rw,,noexec"), WORKING_DIR, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unsupported option ''");
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/scratch", "size=lots"), WORKING_DIR, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid size 'lots'");
    }

    @Test
    void shouldRejectOverlapWithWorkingDirectoryOnlyWhenFilesAreCopied() {
        for (String overlapping : new String[]{"/tmp", WORKING_DIR, WORKING_DIR + "/scratch"}) {
            assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of(overlapping, ""), WORKING_DIR, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps the task working directory");

            assertThat(Docker.tmpfsMounts(Map.of(overlapping, ""), WORKING_DIR, false)).containsOnlyKeys(overlapping);
        }

        assertThat(Docker.tmpfsMounts(Map.of("/tmp/kestra", ""), WORKING_DIR, true)).containsOnlyKeys("/tmp/kestra");
    }
}

package io.kestra.plugin.scripts.runner.docker;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerTmpfsTest {
    private static final String WORKING_DIR = "/tmp/kestra-wd/tmp/abc";

    @Test
    void shouldNormalizePathWhenTrailingSlash() {
        assertThat(Docker.tmpfsMounts(Map.of("/scratch/", " rw,noexec,size=512m ", "/cache", ""), WORKING_DIR, true, true))
            .containsExactlyInAnyOrderEntriesOf(Map.of("/scratch", "rw,noexec,size=512m", "/cache", ""));
    }

    @Test
    void shouldRejectPathWhenRelativeOrRoot() {
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("scratch", ""), WORKING_DIR, false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be an absolute path");
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/", ""), WORKING_DIR, false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("other than the root directory");
    }

    @Test
    void shouldRejectPathWhenDeclaredTwiceOnceNormalized() {
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/scratch", "", "/scratch/", ""), WORKING_DIR, false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The tmpfs mount path '/scratch' is declared more than once.");
    }

    @Test
    void shouldRejectOptionWhenDockerWouldRefuseItAtStart() {
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/scratch", "rw,sizee=1m"), WORKING_DIR, false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The tmpfs mount '/scratch' has an unsupported option 'sizee'.");
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/scratch", "rw,,noexec"), WORKING_DIR, false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unsupported option ''");

        for (String invalid : new String[]{"size=lots", "mode=zzz", "mode=0999", "uid=abc", "gid=notanumber", "nr_inodes=-5", "nr_blocks=", "mpol="}) {
            assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/scratch", invalid), WORKING_DIR, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has an invalid value");
        }
    }

    @Test
    void shouldAcceptOptionValuesWhenWellFormed() {
        var options = "rw,size=50%,mode=1777,uid=1000,gid=0,nr_inodes=10k,nr_blocks=128,mpol=interleave";

        assertThat(Docker.tmpfsMounts(Map.of("/scratch", options), WORKING_DIR, true, true)).containsEntry("/scratch", options);
    }

    @Test
    void shouldRejectPathWhenAtOrInsideSharedWorkingDirectory() {
        for (String inside : new String[]{WORKING_DIR, WORKING_DIR + "/scratch"}) {
            assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of(inside, ""), WORKING_DIR, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is at or inside the task working directory");
        }
    }

    @Test
    void shouldRejectParentOfWorkingDirectoryOnlyWhenFilesAreCopied() {
        assertThatThrownBy(() -> Docker.tmpfsMounts(Map.of("/tmp", ""), WORKING_DIR, true, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlaps the task working directory");

        assertThat(Docker.tmpfsMounts(Map.of("/tmp", ""), WORKING_DIR, true, false)).containsOnlyKeys("/tmp");
    }

    @Test
    void shouldAcceptOverlapWhenWorkingDirectoryIsNeitherBoundNorCopied() {
        for (String overlapping : new String[]{"/tmp", WORKING_DIR, WORKING_DIR + "/scratch"}) {
            assertThat(Docker.tmpfsMounts(Map.of(overlapping, ""), WORKING_DIR, false, false)).containsOnlyKeys(overlapping);
        }

        assertThat(Docker.tmpfsMounts(Map.of("/tmp/kestra", ""), WORKING_DIR, true, true)).containsOnlyKeys("/tmp/kestra");
    }
}

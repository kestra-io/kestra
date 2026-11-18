package io.kestra.plugin.scripts.runner.docker;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerTmpfsTest {
    private static final String WORKING_DIR = "/tmp/kestra-wd/tmp/abc";

    @Test
    void shouldNormalizePathWhenTrailingSlash() {
        assertThat(Tmpfs.mounts(Map.of("/scratch/", " rw,noexec,size=512m ", "/cache", ""), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, true))
            .containsExactlyInAnyOrderEntriesOf(Map.of("/scratch", "rw,noexec,size=512m", "/cache", ""));
    }

    @Test
    void shouldRejectPathWhenRelativeOrRoot() {
        assertThatThrownBy(() -> Tmpfs.mounts(Map.of("scratch", ""), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be an absolute path");
        assertThatThrownBy(() -> Tmpfs.mounts(Map.of("/", ""), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("other than the root directory");
    }

    @Test
    void shouldRejectPathWhenDeclaredTwiceOnceNormalized() {
        assertThatThrownBy(() -> Tmpfs.mounts(Map.of("/scratch", "", "/scratch/", ""), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The tmpfs mount path '/scratch' is declared more than once.");
    }

    @Test
    void shouldRejectOptionWhenDockerWouldRefuseItAtStart() {
        assertThatThrownBy(() -> Tmpfs.mounts(Map.of("/scratch", "rw,sizee=1m"), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The tmpfs mount '/scratch' has an unsupported option 'sizee'. Use size, mode, uid, gid, nr_inodes, nr_blocks, mpol or a mount flag such as ro or noexec.");

        for (String unsupported : new String[]{"rw,,noexec", "bind", "rbind", "remount"}) {
            assertThatThrownBy(() -> Tmpfs.mounts(Map.of("/scratch", unsupported), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has an unsupported option");
        }
    }

    @Test
    void shouldRejectOptionValueWhenMalformed() {
        assertThatThrownBy(() -> Tmpfs.mounts(Map.of("/scratch", "mode=zzz"), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The tmpfs mount '/scratch' has an invalid value 'zzz' for the option 'mode'. Use an octal file mode, for example 1777.");

        for (String invalid : new String[]{"size=lots", "mode=0999", "uid=abc", "gid=notanumber", "nr_inodes=-5", "nr_blocks=", "mpol=bogus"}) {
            assertThatThrownBy(() -> Tmpfs.mounts(Map.of("/scratch", invalid), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has an invalid value");
        }
    }

    @Test
    void shouldAcceptOptionValuesWhenWellFormed() {
        var options = "rw,exec,size=50%,mode=01777,uid=1000,gid=0,nr_inodes=1t,nr_blocks=10k,mpol=bind:0";

        assertThat(Tmpfs.mounts(Map.of("/scratch", options), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, true)).containsEntry("/scratch", options);
    }

    @Test
    void shouldRejectOnlyTheWorkingDirectoryItselfWhenBoundByMount() {
        assertThatThrownBy(() -> Tmpfs.mounts(Map.of(WORKING_DIR, ""), WORKING_DIR, Docker.FileHandlingStrategy.MOUNT, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is the task working directory");

        assertThat(Tmpfs.mounts(Map.of(WORKING_DIR + "/scratch", "", "/tmp", ""), WORKING_DIR, Docker.FileHandlingStrategy.MOUNT, false))
            .containsOnlyKeys(WORKING_DIR + "/scratch", "/tmp");
    }

    @Test
    void shouldRejectAnyOverlapWithWorkingDirectoryWhenFilesAreCopied() {
        for (String overlapping : new String[]{"/tmp", WORKING_DIR, WORKING_DIR + "/scratch"}) {
            assertThatThrownBy(() -> Tmpfs.mounts(Map.of(overlapping, ""), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps the task working directory");
        }
    }

    @Test
    void shouldAcceptOverlapWhenWorkingDirectoryIsNeitherBoundNorCopied() {
        for (String overlapping : new String[]{"/tmp", WORKING_DIR, WORKING_DIR + "/scratch"}) {
            assertThat(Tmpfs.mounts(Map.of(overlapping, ""), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, false)).containsOnlyKeys(overlapping);
        }

        assertThat(Tmpfs.mounts(Map.of("/tmp/kestra", ""), WORKING_DIR, Docker.FileHandlingStrategy.VOLUME, true)).containsOnlyKeys("/tmp/kestra");
    }
}

package io.kestra.core.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static org.assertj.core.api.Assertions.assertThat;

class UnixModeToPosixFilePermissionsTest {

    @Test
    void shouldReturnPosixFilePermissions() {
        assertThat(PosixFilePermissions.toString(UnixModeToPosixFilePermissions.toPosixPermissions(Integer.parseInt("700", 8)))).isEqualTo("rwx------");
        assertThat(PosixFilePermissions.toString(UnixModeToPosixFilePermissions.toPosixPermissions(Integer.parseInt("620", 8)))).isEqualTo("rw--w----");
        assertThat(PosixFilePermissions.toString(UnixModeToPosixFilePermissions.toPosixPermissions(Integer.parseInt("777", 8)))).isEqualTo("rwxrwxrwx");
        assertThat(PosixFilePermissions.toString(UnixModeToPosixFilePermissions.toPosixPermissions(TarArchiveEntry.DEFAULT_FILE_MODE))).isEqualTo("rw-r--r--");
    }

    @Test
    void shouldReturnPosixFilePermissionsFromString() {
        assertThat(Integer.toOctalString(UnixModeToPosixFilePermissions.fromPosixFilePermissions(Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)))).isEqualTo("700");
        assertThat(Integer.toOctalString(UnixModeToPosixFilePermissions.fromPosixFilePermissions(Set.of(OWNER_READ, OWNER_WRITE, GROUP_WRITE)))).isEqualTo("620");
    }
}
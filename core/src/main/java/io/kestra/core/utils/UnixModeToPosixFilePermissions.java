package io.kestra.core.utils;

import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility method for converting Unix file mode into PosixFilePermission.
 */
public final class UnixModeToPosixFilePermissions {

    public static Set<PosixFilePermission> toPosixPermissions(int mode) {
        Set<PosixFilePermission> permissions = new HashSet<>();

        // Check owner permissions
        if ((mode & 0400) != 0) {
            permissions.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0200) != 0) {
            permissions.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0100) != 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }

        // Check group permissions
        if ((mode & 0040) != 0) {
            permissions.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 0020) != 0) {
            permissions.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 0010) != 0) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
        }

        // Check others permissions
        if ((mode & 0004) != 0) {
            permissions.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & 0002) != 0) {
            permissions.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mode & 0001) != 0) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }

        return permissions;
    }

    public static int fromPosixFilePermissions(final Set<PosixFilePermission> perms) {
        int mode = 0;
        for (PosixFilePermission perm : perms) {
            switch (perm) {
                case OWNER_READ:    mode |= 0400; break;
                case OWNER_WRITE:   mode |= 0200; break;
                case OWNER_EXECUTE: mode |= 0100; break;
                case GROUP_READ:    mode |= 0040; break;
                case GROUP_WRITE:   mode |= 0020; break;
                case GROUP_EXECUTE: mode |= 0010; break;
                case OTHERS_READ:   mode |= 0004; break;
                case OTHERS_WRITE:  mode |= 0002; break;
                case OTHERS_EXECUTE:mode |= 0001; break;
            }
        }
        return mode;
    }
}

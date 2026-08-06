package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.exceptions.KestraRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility to inject the embedded <a href="https://github.com/kestra-io/koltp">koltp</a> binary into a file destination.
 * <p>
 * koltp is a single Actually Portable Executable covering Linux, macOS and BSD on amd64 and arm64; the same
 * binary works on Windows once renamed with an {@code .exe} extension. It is embedded at build time by the
 * {@code downloadKoltp} Gradle task of the script module.
 */
public final class KoltpUtils {
    public static final String BINARY_NAME = "koltp";
    public static final String WINDOWS_BINARY_NAME = "koltp.exe";
    private static final String RESOURCE_PATH = "/koltp/koltp";

    private KoltpUtils() {
    }

    /**
     * Copies the embedded koltp binary to the given destination and makes it executable.
     *
     * @param destination the file the binary is written to, overwritten when it already exists
     * @return the destination path
     * @throws IOException when the destination cannot be written
     */
    public static Path copyTo(Path destination) throws IOException {
        try (InputStream binary = KoltpUtils.class.getResourceAsStream(RESOURCE_PATH)) {
            if (binary == null) {
                throw new KestraRuntimeException("Cannot inject the koltp binary: it was not embedded in this build. Run the 'downloadKoltp' Gradle task and rebuild.");
            }

            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(binary, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        setExecutable(destination);
        return destination;
    }

    private static void setExecutable(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(path));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException e) {
            // Windows hosts have no POSIX permissions.
            path.toFile().setExecutable(true, false);
        }
    }
}

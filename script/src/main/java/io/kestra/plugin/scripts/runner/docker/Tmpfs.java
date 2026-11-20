package io.kestra.plugin.scripts.runner.docker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

final class Tmpfs {
    // Options Docker accepts, which it only checks when the container starts, after the image pull and the file upload.
    private static final Set<String> FLAG_OPTIONS = Set.of(
        "defaults", "ro", "rw", "suid", "nosuid", "dev", "nodev", "exec", "noexec", "sync", "async", "dirsync",
        "mand", "nomand", "atime", "noatime", "diratime", "nodiratime", "unbindable", "runbindable",
        "private", "rprivate", "shared", "rshared", "slave", "rslave", "relatime", "norelatime", "strictatime", "nostrictatime"
    );
    private static final Map<String, ValueOption> VALUE_OPTIONS = Map.of(
        "size", new ValueOption("\\d+([kKmMgGtTpPeE]|%)?", "a number of bytes with an optional k, m or g suffix, or a percentage of the host memory, for example 512m"),
        "nr_blocks", new ValueOption("\\d+[kKmMgGtTpPeE]?", "a non-negative integer with an optional k, m or g suffix, for example 10k"),
        "nr_inodes", new ValueOption("\\d+[kKmMgGtTpPeE]?", "a non-negative integer with an optional k, m or g suffix, for example 10k"),
        "mode", new ValueOption("0*[0-7]{1,4}", "an octal file mode, for example 1777"),
        "uid", new ValueOption("\\d+", "a non-negative integer, for example 1000"),
        "gid", new ValueOption("\\d+", "a non-negative integer, for example 1000"),
        "mpol", new ValueOption("(default|prefer|bind|interleave|local)([=:].*)?", "a memory policy among default, prefer, bind, interleave and local, for example interleave")
    );
    private static final String SUPPORTED_OPTIONS = "Use size, mode, uid, gid, nr_inodes, nr_blocks, mpol or a mount flag such as ro or noexec.";

    private Tmpfs() {
    }

    static Map<String, String> mounts(Map<String, String> tmpfs, String workingDirectory, Docker.FileHandlingStrategy strategy, boolean hasFilesToCopy) {
        // MOUNT binds the working directory, and VOLUME cannot copy files through a tmpfs at, inside or above it.
        var isWorkingDirectoryBound = strategy == Docker.FileHandlingStrategy.MOUNT;
        var isWorkingDirectoryCopied = hasFilesToCopy && strategy == Docker.FileHandlingStrategy.VOLUME;
        var mounts = new LinkedHashMap<String, String>(tmpfs.size());
        for (var entry : tmpfs.entrySet()) {
            var path = FilenameUtils.normalizeNoEndSeparator(entry.getKey(), true);
            if (path == null || !path.startsWith("/") || "/".equals(path)) {
                throw new IllegalArgumentException("The tmpfs mount path '%s' is invalid: it must be an absolute path other than the root directory.".formatted(entry.getKey()));
            }
            if (mounts.containsKey(path)) {
                throw new IllegalArgumentException("The tmpfs mount path '%s' is declared more than once.".formatted(path));
            }
            if (isWorkingDirectoryBound && path.equals(workingDirectory)) {
                throw new IllegalArgumentException(
                    "The tmpfs mount path '%s' is the task working directory, which the MOUNT file handling strategy already binds. Mount it elsewhere, for example inside the working directory."
                        .formatted(path)
                );
            }
            if (isWorkingDirectoryCopied && (path.equals(workingDirectory) || path.startsWith(workingDirectory + "/") || workingDirectory.startsWith(path + "/"))) {
                throw new IllegalArgumentException(
                    "The tmpfs mount path '%s' overlaps the task working directory '%s', so its files would be lost with the VOLUME file handling strategy. Mount it elsewhere, or use the MOUNT strategy."
                        .formatted(path, workingDirectory)
                );
            }

            var options = entry.getValue() == null ? "" : entry.getValue().strip();
            if (!options.isEmpty()) {
                for (var option : options.split(",", -1)) {
                    validateOption(path, option);
                }
            }
            mounts.put(path, options);
        }
        return mounts;
    }

    private static void validateOption(String path, String option) {
        var separator = option.indexOf('=');
        if (separator < 0) {
            if (!FLAG_OPTIONS.contains(option)) {
                throw new IllegalArgumentException("The tmpfs mount '%s' has an unsupported option '%s'. %s".formatted(path, option, SUPPORTED_OPTIONS));
            }
            return;
        }

        var name = option.substring(0, separator);
        var valueOption = VALUE_OPTIONS.get(name);
        if (valueOption == null) {
            throw new IllegalArgumentException("The tmpfs mount '%s' has an unsupported option '%s'. %s".formatted(path, name, SUPPORTED_OPTIONS));
        }
        var value = option.substring(separator + 1);
        if (!valueOption.pattern().matcher(value).matches()) {
            throw new IllegalArgumentException(
                "The tmpfs mount '%s' has an invalid value '%s' for the option '%s'. Use %s.".formatted(path, value, name, valueOption.format())
            );
        }
    }

    private record ValueOption(Pattern pattern, String format) {
        ValueOption(String regex, String format) {
            this(Pattern.compile(regex), format);
        }
    }
}

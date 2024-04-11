package io.kestra.cli.commands.flows;

import com.google.common.io.Files;
import lombok.SneakyThrows;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Deprecated
public abstract class IncludeHelperExpander {

    public static String expand(String value, Path directory) throws IOException {
        return value.lines()
            .map(line -> line.contains("[[>") && line.contains("]]") ? expandLine(line, directory) : line)
            .collect(Collectors.joining("\n"));
    }

    @SneakyThrows
    private static String expandLine(String line, Path directory) {
        String prefix = line.substring(0, line.indexOf("[[>"));
        String suffix = line.substring(line.indexOf("]]") + 2, line.length());
        String file = line.substring(line.indexOf("[[>") + 3 , line.indexOf("]]")).strip();
        Path includePath = directory.resolve(file);
        List<String> include = Files.readLines(includePath.toFile(), Charset.defaultCharset());

        // handle single line directly with the suffix (should be between quotes or double-quotes
        if(include.size() == 1) {
            String singleInclude = include.get(0);
            return prefix + singleInclude + suffix;
        }

        // multi-line will be expanded with the prefix but no suffix
        return include.stream()
            .map(includeLine -> prefix + includeLine)
            .collect(Collectors.joining("\n"));
    }
}

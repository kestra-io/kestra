package io.kestra.core.runners.pebble;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunVariables;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift detector for {@code ui/src/assets/docs/basic.md} — the editor doc panel shown in the Kestra UI.
 * <p>
 * Forward direction only: every registered Pebble filter, function and execution-context path must
 * appear in the markdown. Extras (Pebble tags, operators, syntax samples, EE-only items, examples
 * like {@code {{ inputs.myInput }}}) are tolerated — that's why the doc is hand-curated.
 * <p>
 * When this fails, the message names the missing entry and the table to update — fix in the same PR
 * that added the registry entry.
 */
@KestraTest
class PebbleDocBasicMdDriftTest {

    private static final Path DOC = locateBasicMd();
    private static final String MD = readDoc();

    @Inject
    PebbleExpressionService pebble;

    @Test
    void everyRegisteredFilterAppearsInBasicMd() {
        assertThat(pebble.filters())
            .as("registry must be populated — empty means @KestraTest didn't init the @Context bean")
            .isNotEmpty();

        List<String> missing = pebble.filters().stream()
            .filter(name -> !MD.contains("| `" + name + "`"))
            .toList();

        assertThat(missing)
            .as("Filters registered but missing from %s — add a row per name to the 'Pebble functions, filters and tags' table", DOC)
            .isEmpty();
    }

    @Test
    void everyRegisteredFunctionAppearsInBasicMd() {
        assertThat(pebble.functions()).isNotEmpty();

        List<String> missing = pebble.functions().stream()
            .map(PebbleFunction::name)
            .filter(name -> !MD.contains("| `" + name + "`"))
            .toList();

        assertThat(missing)
            .as("Functions registered but missing from %s — add a row per name to the 'Pebble functions, filters and tags' table", DOC)
            .isEmpty();
    }

    @Test
    void everyExecutionContextPathAppearsInBasicMd() {
        assertThat(RunVariables.EXECUTION_CONTEXT_PATHS).isNotEmpty();

        // A path is considered documented if basic.md contains either the bare backticked expression
        // (`{{ path }}`) or the start of an extension expression (`{{ path.…). The latter covers
        // structural containers (`flow`, `execution`, …) and dynamic top-level keys (`inputs`, `outputs`, …)
        // for which basic.md only shows children.
        List<String> missing = RunVariables.EXECUTION_CONTEXT_PATHS.stream()
            .filter(path -> !MD.contains("`{{ " + path + " }}`") && !MD.contains("`{{ " + path + "."))
            .toList();

        assertThat(missing)
            .as("Context paths in EXECUTION_CONTEXT_PATHS but missing from %s — add a row per path to the 'Common Pebble expressions' table", DOC)
            .isEmpty();
    }

    private static Path locateBasicMd() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("ui/src/assets/docs/basic.md"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Cannot locate ui/src/assets/docs/basic.md walking up from " + Paths.get("").toAbsolutePath());
        }
        return p.resolve("ui/src/assets/docs/basic.md");
    }

    private static String readDoc() {
        try {
            return Files.readString(DOC);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + DOC, e);
        }
    }
}

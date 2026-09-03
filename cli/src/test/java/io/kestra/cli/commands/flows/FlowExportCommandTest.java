package io.kestra.cli.commands.flows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.core.migration.MigrationRunnerInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;

import static org.assertj.core.api.Assertions.assertThat;

class FlowExportCommandTest {
    @Test
    void shouldExportOnlyTheRequestedNamespace(@TempDir Path exportDirectory) throws Exception {
        // holds 3 flows in `io.kestra.cli`, 1 in the child namespace `io.kestra.cli.sub`, and 1 in the
        // unrelated `io.kestra.outsider` which must not be exported
        URL directory = FlowExportCommandTest.class.getClassLoader().getResource("flows");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            ctx.getBean(MigrationRunnerInterface.class).runAlways();

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            // load the flows
            LocalFlowRepositoryLoader flowRepositoryLoader = ctx.getBean(LocalFlowRepositoryLoader.class);
            flowRepositoryLoader.load(directory);

            // then we export them
            PicocliRunner.call(FlowExportCommand.class, ctx, exportArgs(embeddedServer, "io.kestra.cli", exportDirectory));

            Path zipFile = exportDirectory.resolve("flows.zip");
            assertThat(zipFile).exists();

            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                List<String> entries = zip.stream().map(ZipEntry::getName).toList();

                assertThat(entries)
                    .as("the flows of the requested namespace are exported")
                    .contains("io.kestra.cli-first.yml", "io.kestra.cli-second.yml", "io.kestra.cli-third.yml");

                assertThat(entries)
                    .as("the child namespaces are exported too, as in the namespace-scoped flow list of the UI")
                    .contains("io.kestra.cli.sub-child.yml");

                // Other test classes sharing the H2 database may have added more `io.kestra.cli` flows, so only
                // the namespace of every entry can be asserted, not the exact entry list. Entries are named
                // `<namespace>-<id>.yml`, so the trailing `[-.]` keeps a sibling such as `io.kestra.clix` out.
                assertThat(entries)
                    .as("--namespace filters the export instead of being silently ignored")
                    .allSatisfy(entry -> assertThat(entry).matches("io\\.kestra\\.cli[-.].*"));
            }

            // a namespace holding no flow exports an empty archive rather than the whole tenant
            Path emptyExportDirectory = exportDirectory.resolve("empty");
            Files.createDirectory(emptyExportDirectory);
            PicocliRunner.call(FlowExportCommand.class, ctx, exportArgs(embeddedServer, "io.kestra.unknown", emptyExportDirectory));

            try (ZipFile zip = new ZipFile(emptyExportDirectory.resolve("flows.zip").toFile())) {
                assertThat(zip.stream().map(ZipEntry::getName))
                    .as("an unknown namespace exports nothing")
                    .isEmpty();
            }
        }
    }

    private static String[] exportArgs(EmbeddedServer embeddedServer, String namespace, Path directory) {
        return new String[]{
            "--plugins",
            "/tmp", // pass this arg because it can cause failure
            "--server",
            embeddedServer.getURL().toString(),
            "--user",
            "myuser:pass:word",
            "--namespace",
            namespace,
            directory.toString(),
        };
    }
}

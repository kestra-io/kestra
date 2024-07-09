package io.kestra.cli.commands.namespaces.kv;

import io.kestra.cli.commands.namespaces.files.NamespaceFilesCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;

class KvCommandTest {
    @Test
    void runWithNoParam() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            String[] args = {};
            Integer call = PicocliRunner.call(NamespaceFilesCommand.class, ctx, args);

            assertThat(call, is(0));
            assertThat(out.toString(), containsString("Usage: kestra namespace kv"));
        }
    }
}

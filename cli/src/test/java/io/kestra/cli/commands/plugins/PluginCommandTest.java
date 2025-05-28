package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class PluginCommandTest {

    @Test
    void shouldGetHelps() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            PicocliRunner.call(PluginCommand.class, ctx);

            assertThat(out.toString()).contains("Usage: kestra plugins");
        }
    }
    
    
    // Additional Coverage:
    @Test
    void shouldListSubcommandsInHelp() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            PluginCommand cmd = new PluginCommand();
            cmd.call();
            String output = out.toString();
            assertThat(output).contains("install");
            assertThat(output).contains("uninstall");
            assertThat(output).contains("list");
            assertThat(output).contains("doc");
            assertThat(output).contains("search");
        } finally {
            System.setOut(originalOut);
        }
    }

    // Passes
    @Test
    void shouldNotLoadExternalPlugins() {
        PluginCommand cmd = new PluginCommand();
        assertThat(cmd.loadExternalPlugins()).isFalse();
    }
}

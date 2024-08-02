package io.kestra.cli.commands.namespaces.kv;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValue;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class KvUpdateCommandTest {
    @Test
    void string() throws IOException, ResourceExpiredException {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                "string",
                "stringValue"
            };
            PicocliRunner.call(KvUpdateCommand.class, ctx, args);

            KVStoreService kvStoreService = ctx.getBean(KVStoreService.class);
            KVStore kvStore = kvStoreService.get(null, "io.kestra.cli", null);

            assertThat(kvStore.getValue("string").get(), is(new KVValue("stringValue")));
            assertThat(((InternalKVStore)kvStore).getRawValue("string").get(), is("\"stringValue\""));
        }
    }

    @Test
    void integer() throws IOException, ResourceExpiredException {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                "int",
                "1"
            };
            PicocliRunner.call(KvUpdateCommand.class, ctx, args);

            KVStoreService kvStoreService = ctx.getBean(KVStoreService.class);
            KVStore kvStore = kvStoreService.get(null, "io.kestra.cli", null);

            assertThat(kvStore.getValue("int").get(), is(new KVValue(1)));
            assertThat(((InternalKVStore)kvStore).getRawValue("int").get(), is("1"));
        }
    }

    @Test
    void integerStr() throws IOException, ResourceExpiredException {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                "intStr",
                "1",
                "-t",
                "STRING"
            };
            PicocliRunner.call(KvUpdateCommand.class, ctx, args);

            KVStoreService kvStoreService = ctx.getBean(KVStoreService.class);
            KVStore kvStore = kvStoreService.get(null, "io.kestra.cli", null);

            assertThat(kvStore.getValue("intStr").get(), is(new KVValue("1")));
            assertThat(((InternalKVStore)kvStore).getRawValue("intStr").get(), is("\"1\""));
        }
    }

    @Test
    void object() throws IOException, ResourceExpiredException {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                "object",
                "{\"some\":\"json\"}",
            };
            PicocliRunner.call(KvUpdateCommand.class, ctx, args);

            KVStoreService kvStoreService = ctx.getBean(KVStoreService.class);
            KVStore kvStore = kvStoreService.get(null, "io.kestra.cli", null);

            assertThat(kvStore.getValue("object").get(), is(new KVValue(Map.of("some", "json"))));
            assertThat(((InternalKVStore)kvStore).getRawValue("object").get(), is("{some:\"json\"}"));
        }
    }

    @Test
    void objectStr() throws IOException, ResourceExpiredException {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                "objectStr",
                "{\"some\":\"json\"}",
                "-t",
                "STRING"
            };
            PicocliRunner.call(KvUpdateCommand.class, ctx, args);

            KVStoreService kvStoreService = ctx.getBean(KVStoreService.class);
            KVStore kvStore = kvStoreService.get(null, "io.kestra.cli", null);

            assertThat(kvStore.getValue("objectStr").get(), is(new KVValue("{\"some\":\"json\"}")));
            assertThat(((InternalKVStore)kvStore).getRawValue("objectStr").get(), is("\"{\\\"some\\\":\\\"json\\\"}\""));
        }
    }

    @Test
    void fromFile() throws IOException, ResourceExpiredException {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            File file = File.createTempFile("objectFromFile", ".json");
            file.createNewFile();
            file.deleteOnExit();
            Files.write(file.toPath(), "{\"some\":\"json\",\"from\":\"file\"}".getBytes());

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                "objectFromFile",
                "valueThatWillGetOverriden",
                "-f " + file.getAbsolutePath()
            };
            PicocliRunner.call(KvUpdateCommand.class, ctx, args);

            KVStoreService kvStoreService = ctx.getBean(KVStoreService.class);
            KVStore kvStore = kvStoreService.get(null, "io.kestra.cli", null);

            assertThat(kvStore.getValue("objectFromFile").get(), is(new KVValue(Map.of("some", "json", "from", "file"))));
            assertThat(((InternalKVStore)kvStore).getRawValue("objectFromFile").get(), is("{some:\"json\",from:\"file\"}"));
        }
    }
}
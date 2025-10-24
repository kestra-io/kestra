package io.kestra.cli.commands.migrations;

import io.kestra.cli.App;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import io.kestra.core.storages.kv.*;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.log.Log;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class MetadataMigrationCommandTest {
    @Test
    void run() throws IOException, ResourceExpiredException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String namespace = TestsUtils.randomNamespace();
            String key = "myKey";
            StorageInterface storage = ctx.getBean(StorageInterface.class);
            String description = "Some description";
            String value = "someValue";
            putOldKv(storage, namespace, key, description, value);

            String anotherNamespace = TestsUtils.randomNamespace();
            String anotherKey = "anotherKey";
            String anotherDescription = "another description";
            putOldKv(storage, anotherNamespace, anotherKey, anotherDescription, "anotherValue");

            KvMetadataRepositoryInterface kvMetadataRepository = ctx.getBean(KvMetadataRepositoryInterface.class);
            String tenantId = TenantService.MAIN_TENANT;
            assertThat(kvMetadataRepository.findByName(tenantId, namespace, key).isPresent()).isFalse();

            String[] kvMetadataMigrationCommand = {
                "migrate", "metadata"
            };
            PicocliRunner.call(App.class, ctx, kvMetadataMigrationCommand);


            assertThat(out.toString()).contains("✅ Metadata migration complete.");
            // Still it's not in the metadata repository because no flow exist to find that secret
            assertThat(kvMetadataRepository.findByName(tenantId, namespace, key).isPresent()).isFalse();
            assertThat(kvMetadataRepository.findByName(tenantId, anotherNamespace, anotherKey).isPresent()).isFalse();

            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);
            flowRepository.create(GenericFlow.of(Flow.builder()
                .tenantId(tenantId)
                .id("a-flow")
                .namespace(namespace)
                .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("logging").build()))
                .build()));

            out.reset();
            PicocliRunner.call(App.class, ctx, kvMetadataMigrationCommand);

            assertThat(out.toString()).contains("✅ Metadata migration complete.");
            Optional<PersistedKvMetadata> foundSecret = kvMetadataRepository.findByName(tenantId, namespace, key);
            assertThat(foundSecret.isPresent()).isTrue();
            assertThat(foundSecret.get().getDescription()).isEqualTo(description);
            assertThat(kvMetadataRepository.findByName(tenantId, anotherNamespace, anotherKey).isPresent()).isFalse();

            KVStore kvStore = new InternalKVStore(tenantId, namespace, storage, kvMetadataRepository);
            Optional<KVEntry> actualKv = kvStore.get(key);
            assertThat(actualKv.isPresent()).isTrue();
            assertThat(actualKv.get().description()).isEqualTo(description);

            Optional<KVValue> actualValue = kvStore.getValue(key);
            assertThat(actualValue.isPresent()).isTrue();
            assertThat(actualValue.get().value()).isEqualTo(value);
        }
    }

    private static void putOldKv(StorageInterface storage, String namespace, String key, String description, String value) throws IOException {
        URI kvStorageUri = URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.kvPrefix(namespace) + "/" + key + ".ion");
        KVValueAndMetadata kvValueAndMetadata = new KVValueAndMetadata(new KVMetadata(description, Duration.ofMinutes(5)), value);
        storage.put(TenantService.MAIN_TENANT, namespace, kvStorageUri, new StorageObject(
            kvValueAndMetadata.metadataAsMap(),
            new ByteArrayInputStream(JacksonMapper.ofIon().writeValueAsBytes(kvValueAndMetadata.value()))
        ));
    }
}

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
import io.micronaut.core.annotation.NonNull;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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

            String tenantId = TenantService.MAIN_TENANT;

            // Expired KV should not be migrated + should be purged from the storage
            String expiredKey = "expiredKey";
            putOldKv(storage, namespace, expiredKey, Instant.now().minus(Duration.ofMinutes(5)), "some expired description", "expiredValue");
            assertThat(storage.exists(tenantId, null, getKvStorageUri(namespace, expiredKey))).isTrue();

            KvMetadataRepositoryInterface kvMetadataRepository = ctx.getBean(KvMetadataRepositoryInterface.class);
            assertThat(kvMetadataRepository.findByName(tenantId, namespace, key).isPresent()).isFalse();

            String[] kvMetadataMigrationCommand = {
                "migrate", "metadata"
            };
            PicocliRunner.call(App.class, ctx, kvMetadataMigrationCommand);


            assertThat(out.toString()).contains("✅ Metadata migration complete.");
            // Still it's not in the metadata repository because no flow exist to find that kv
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
            Optional<PersistedKvMetadata> foundKv = kvMetadataRepository.findByName(tenantId, namespace, key);
            assertThat(foundKv.isPresent()).isTrue();
            assertThat(foundKv.get().getDescription()).isEqualTo(description);
            assertThat(kvMetadataRepository.findByName(tenantId, anotherNamespace, anotherKey).isPresent()).isFalse();

            KVStore kvStore = new InternalKVStore(tenantId, namespace, storage, kvMetadataRepository);
            Optional<KVEntry> actualKv = kvStore.get(key);
            assertThat(actualKv.isPresent()).isTrue();
            assertThat(actualKv.get().description()).isEqualTo(description);

            Optional<KVValue> actualValue = kvStore.getValue(key);
            assertThat(actualValue.isPresent()).isTrue();
            assertThat(actualValue.get().value()).isEqualTo(value);

            assertThat(kvMetadataRepository.findByName(tenantId, namespace, expiredKey).isPresent()).isFalse();
            assertThat(storage.exists(tenantId, null, getKvStorageUri(namespace, expiredKey))).isFalse();
        }
    }

    private static void putOldKv(StorageInterface storage, String namespace, String key, String description, String value) throws IOException {
        putOldKv(storage, namespace, key, Instant.now().plus(Duration.ofMinutes(5)), description, value);
    }

    private static void putOldKv(StorageInterface storage, String namespace, String key, Instant expirationDate, String description, String value) throws IOException {
        URI kvStorageUri = getKvStorageUri(namespace, key);
        KVValueAndMetadata kvValueAndMetadata = new KVValueAndMetadata(new KVMetadata(description, expirationDate), value);
        storage.put(TenantService.MAIN_TENANT, namespace, kvStorageUri, new StorageObject(
            kvValueAndMetadata.metadataAsMap(),
            new ByteArrayInputStream(JacksonMapper.ofIon().writeValueAsBytes(kvValueAndMetadata.value()))
        ));
    }

    private static @NonNull URI getKvStorageUri(String namespace, String key) {
        return URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.kvPrefix(namespace) + "/" + key + ".ion");
    }
}

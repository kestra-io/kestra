package io.kestra.cli.commands.migrations.metadata;

import io.kestra.cli.App;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.*;
import io.kestra.core.storages.kv.*;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.log.Log;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NsFilesMetadataMigrationCommandTest {
    @Test
    void run() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            /* Initial setup:
            * - namespace 1: my/path, value
            * - namespace 1: another/path
            * - namespace 2: yet/another/path
            * - Nothing in database */
            String namespace = TestsUtils.randomNamespace();
            String path = "/my/path";
            StorageInterface storage = ctx.getBean(StorageInterface.class);
            String value = "someValue";
            putOldNsFile(storage, namespace, path, value);

            String anotherPath = "/another/path";
            String anotherValue = "anotherValue";
            putOldNsFile(storage, namespace, anotherPath, anotherValue);

            String anotherNamespace = TestsUtils.randomNamespace();
            String yetAnotherPath = "/yet/another/path";
            String yetAnotherValue = "yetAnotherValue";
            putOldNsFile(storage, anotherNamespace, yetAnotherPath, yetAnotherValue);

            NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository = ctx.getBean(NamespaceFileMetadataRepositoryInterface.class);
            String tenantId = TenantService.MAIN_TENANT;
            assertThat(namespaceFileMetadataRepository.findByPath(tenantId, namespace, path).isPresent()).isFalse();

            /* Expected outcome from the migration command:
            * - no namespace files has been migrated because no flow exist in the namespace so they are not picked up because we don't know they exist */
            String[] nsFilesMetadataMigrationCommand = {
                "migrate", "metadata", "nsfiles"
            };
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);


            assertThat(out.toString()).contains("✅ Namespace Files Metadata migration complete.");
            // Still it's not in the metadata repository because no flow exist to find that namespace file
            assertThat(namespaceFileMetadataRepository.findByPath(tenantId, namespace, path).isPresent()).isFalse();
            assertThat(namespaceFileMetadataRepository.findByPath(tenantId, namespace, anotherPath).isPresent()).isFalse();
            assertThat(namespaceFileMetadataRepository.findByPath(tenantId, anotherNamespace, yetAnotherPath).isPresent()).isFalse();

            // A flow is created from namespace 1, so the namespace files in this namespace should be migrated
            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);
            flowRepository.create(GenericFlow.of(Flow.builder()
                .tenantId(tenantId)
                .id("a-flow")
                .namespace(namespace)
                .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("logging").build()))
                .build()));

            /* We run the migration again:
            * - namespace 1 my/path file is seen and metadata is migrated to database
            * - namespace 1 another/path file is seen and metadata is migrated to database
            * - namespace 2 yet/another/path is not seen because no flow exist in this namespace */
            out.reset();
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);

            assertThat(out.toString()).contains("✅ Namespace Files Metadata migration complete.");
            Optional<NamespaceFileMetadata> foundNsFile = namespaceFileMetadataRepository.findByPath(tenantId, namespace, path);
            assertThat(foundNsFile.isPresent()).isTrue();
            assertThat(foundNsFile.get().getVersion()).isEqualTo(1);
            assertThat(foundNsFile.get().getSize()).isEqualTo(value.length());

            Optional<NamespaceFileMetadata> anotherFoundNsFile = namespaceFileMetadataRepository.findByPath(tenantId, namespace, anotherPath);
            assertThat(anotherFoundNsFile.isPresent()).isTrue();
            assertThat(anotherFoundNsFile.get().getVersion()).isEqualTo(1);
            assertThat(anotherFoundNsFile.get().getSize()).isEqualTo(anotherValue.length());

            NamespaceFactory namespaceFactory = ctx.getBean(NamespaceFactory.class);
            Namespace namespaceStorage = namespaceFactory.of(tenantId, namespace, storage);
            FileAttributes nsFileRawMetadata = namespaceStorage.getFileMetadata(Path.of(path));
            assertThat(nsFileRawMetadata.getSize()).isEqualTo(value.length());
            assertThat(new String(namespaceStorage.getFileContent(Path.of(path)).readAllBytes())).isEqualTo(value);

            FileAttributes anotherNsFileRawMetadata = namespaceStorage.getFileMetadata(Path.of(anotherPath));
            assertThat(anotherNsFileRawMetadata.getSize()).isEqualTo(anotherValue.length());
            assertThat(new String(namespaceStorage.getFileContent(Path.of(anotherPath)).readAllBytes())).isEqualTo(anotherValue);

            assertThat(namespaceFileMetadataRepository.findByPath(tenantId, anotherNamespace, yetAnotherPath).isPresent()).isFalse();
            assertThatThrownBy(() -> namespaceStorage.getFileMetadata(Path.of(yetAnotherPath))).isInstanceOf(FileNotFoundException.class);

            /* We run one last time the migration without any change to verify that we don't resave an existing metadata.
            * It covers the case where user didn't perform the migrate command yet but they played and added some KV from the UI (so those ones will already be in metadata database). */
            out.reset();
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);

            assertThat(out.toString()).contains("✅ Namespace Files Metadata migration complete.");
            foundNsFile = namespaceFileMetadataRepository.findByPath(tenantId, namespace, path);
            assertThat(foundNsFile.get().getVersion()).isEqualTo(1);
        }
    }

    @Test
    void namespaceWithoutNsFile() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String tenantId = TenantService.MAIN_TENANT;
            String namespace = TestsUtils.randomNamespace();

            // A flow is created from namespace 1, so the namespace files in this namespace should be migrated
            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);
            flowRepository.create(GenericFlow.of(Flow.builder()
                .tenantId(tenantId)
                .id("a-flow")
                .namespace(namespace)
                .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("logging").build()))
                .build()));

            String[] nsFilesMetadataMigrationCommand = {
                "migrate", "metadata", "nsfiles"
            };
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);

            assertThat(out.toString()).contains("✅ Namespace Files Metadata migration complete.");
            assertThat(err.toString()).doesNotContain("java.nio.file.NoSuchFileException");
        }
    }

    private static void putOldNsFile(StorageInterface storage, String namespace, String path, String value) throws IOException {
        URI nsFileStorageUri = getNsFileStorageUri(namespace, path);
        storage.put(TenantService.MAIN_TENANT, namespace, nsFileStorageUri, new StorageObject(
            null,
            new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))
        ));
    }

    private static @NonNull URI getNsFileStorageUri(String namespace, String path) {
        return URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.namespaceFilePrefix(namespace) + path);
    }
}

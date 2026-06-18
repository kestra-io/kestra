package io.kestra.cli.commands.migrations.metadata;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.cli.App;
import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.storages.*;
import io.kestra.core.storages.kv.*;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Pageable;

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
            /*
             * Initial setup:
             * - namespace 1: my/path, value
             * - namespace 1: another/path
             * - namespace 2: yet/another/path
             * - Nothing in database
             */
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

            /*
             * Expected outcome from the migration command:
             * - no namespace files has been migrated because no flow exist in the namespace so they are not picked up because we don't know they exist
             */
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
            flowRepository.create(
                GenericFlow.of(
                    Flow.builder()
                        .tenantId(tenantId)
                        .id("a-flow")
                        .namespace(namespace)
                        .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("logging").build()))
                        .build()
                )
            );

            /*
             * We run the migration again:
             * - namespace 1 my/path file is seen and metadata is migrated to database
             * - namespace 1 another/path file is seen and metadata is migrated to database
             * - namespace 2 yet/another/path is not seen because no flow exist in this namespace
             */
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

            /*
             * We run one last time the migration without any change to verify that we don't resave an existing metadata.
             * It covers the case where user didn't perform the migrate command yet but they played and added some KV from the UI (so those ones will already be in metadata database).
             */
            out.reset();
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);

            assertThat(out.toString()).contains("✅ Namespace Files Metadata migration complete.");
            foundNsFile = namespaceFileMetadataRepository.findByPath(tenantId, namespace, path);
            assertThat(foundNsFile.get().getVersion()).isEqualTo(1);
        }
    }

    @Test
    void shouldReconstructVersionHistoryFromStorageRevisions() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String tenantId = TenantService.MAIN_TENANT;
            String namespace = TestsUtils.randomNamespace();
            StorageInterface storage = ctx.getBean(StorageInterface.class);

            /*
             * A file that went through three versions. v1 is stored under the bare path, v2 and v3
             * under ".vN" suffixes. It lives in a subdirectory to mirror the reported issue (#8665).
             */
            String path = "/scripts/main.py";
            putOldNsFile(storage, namespace, path, "v1");
            putOldNsFile(storage, namespace, path + ".v2", "v2-content");
            putOldNsFile(storage, namespace, path + ".v3", "v3-longer-content");

            // The namespace must own a flow, otherwise the migration does not pick it up.
            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);
            flowRepository.create(
                GenericFlow.of(
                    Flow.builder()
                        .tenantId(tenantId)
                        .id("a-flow")
                        .namespace(namespace)
                        .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("logging").build()))
                        .build()
                )
            );

            String[] nsFilesMetadataMigrationCommand = { "migrate", "metadata", "nsfiles" };
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);

            NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository = ctx.getBean(NamespaceFileMetadataRepositoryInterface.class);

            // The latest version is recorded as version 3 and flagged as the last one.
            Optional<NamespaceFileMetadata> last = namespaceFileMetadataRepository.findByPath(tenantId, namespace, path);
            assertThat(last.isPresent()).isTrue();
            assertThat(last.get().getVersion()).isEqualTo(3);
            assertThat(last.get().isLast()).isTrue();
            assertThat(last.get().getSize()).isEqualTo("v3-longer-content".length());

            // All three versions are now visible: this is what PurgeFiles relies on to purge old ones.
            List<QueryFilter> namespaceFilter = List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.NAMESPACE)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(namespace)
                    .build()
            );
            assertThat(versionsOf(namespaceFileMetadataRepository, tenantId, namespaceFilter, path))
                .containsExactlyInAnyOrder(1, 2, 3);

            // Running the migration again is idempotent: still three versions, not six.
            out.reset();
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);
            assertThat(namespaceFileMetadataRepository.findByPath(tenantId, namespace, path).get().getVersion()).isEqualTo(3);
            assertThat(versionsOf(namespaceFileMetadataRepository, tenantId, namespaceFilter, path))
                .containsExactlyInAnyOrder(1, 2, 3);
        }
    }

    @Test
    void shouldBackfillVersionsForAlreadyMigratedFile() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String tenantId = TenantService.MAIN_TENANT;
            String namespace = TestsUtils.randomNamespace();
            StorageInterface storage = ctx.getBean(StorageInterface.class);

            /*
             * Storage holds the full history (bare + .v2 + .v3), as it would on a real instance...
             */
            String path = "/scripts/main.py";
            putOldNsFile(storage, namespace, path, "v1");
            putOldNsFile(storage, namespace, path + ".v2", "v2-content");
            putOldNsFile(storage, namespace, path + ".v3", "v3-longer-content");

            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);
            flowRepository.create(
                GenericFlow.of(
                    Flow.builder()
                        .tenantId(tenantId)
                        .id("a-flow")
                        .namespace(namespace)
                        .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("logging").build()))
                        .build()
                )
            );

            NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository = ctx.getBean(NamespaceFileMetadataRepositoryInterface.class);

            /*
             * ...but the metadata table only has the bare file as v1, exactly as left behind by the
             * previous (broken) migration that discarded the ".vN" revisions.
             */
            FileAttributes v1Attributes = storage.getAttributes(tenantId, namespace, getNsFileStorageUri(namespace, path));
            namespaceFileMetadataRepository.save(NamespaceFileMetadata.of(tenantId, namespace, path, v1Attributes));
            assertThat(namespaceFileMetadataRepository.findByPath(tenantId, namespace, path).get().getVersion()).isEqualTo(1);

            // Running the fixed migration backfills the missing v2 and v3 revisions.
            String[] nsFilesMetadataMigrationCommand = { "migrate", "metadata", "nsfiles" };
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);

            Optional<NamespaceFileMetadata> last = namespaceFileMetadataRepository.findByPath(tenantId, namespace, path);
            assertThat(last.isPresent()).isTrue();
            assertThat(last.get().getVersion()).isEqualTo(3);
            assertThat(last.get().isLast()).isTrue();
            assertThat(last.get().getSize()).isEqualTo("v3-longer-content".length());

            // The full history is now visible, so PurgeFiles can purge the old versions.
            List<QueryFilter> namespaceFilter = List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.NAMESPACE)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(namespace)
                    .build()
            );
            assertThat(versionsOf(namespaceFileMetadataRepository, tenantId, namespaceFilter, path))
                .containsExactlyInAnyOrder(1, 2, 3);

            // Re-running stays idempotent: still three versions, no duplicate backfill.
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);
            assertThat(versionsOf(namespaceFileMetadataRepository, tenantId, namespaceFilter, path))
                .containsExactlyInAnyOrder(1, 2, 3);
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
            flowRepository.create(
                GenericFlow.of(
                    Flow.builder()
                        .tenantId(tenantId)
                        .id("a-flow")
                        .namespace(namespace)
                        .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("logging").build()))
                        .build()
                )
            );

            String[] nsFilesMetadataMigrationCommand = {
                "migrate", "metadata", "nsfiles"
            };
            PicocliRunner.call(App.class, ctx, nsFilesMetadataMigrationCommand);

            assertThat(out.toString()).contains("✅ Namespace Files Metadata migration complete.");
            assertThat(err.toString()).doesNotContain("java.nio.file.NoSuchFileException");
        }
    }

    private static List<Integer> versionsOf(NamespaceFileMetadataRepositoryInterface repository, String tenantId, List<QueryFilter> namespaceFilter, String path) {
        return repository.find(Pageable.UNPAGED, tenantId, namespaceFilter, true, FetchVersion.ALL).stream()
            .filter(metadata -> metadata.getPath().equals(path))
            .map(NamespaceFileMetadata::getVersion)
            .toList();
    }

    private static void putOldNsFile(StorageInterface storage, String namespace, String path, String value) throws IOException {
        URI nsFileStorageUri = getNsFileStorageUri(namespace, path);
        storage.put(
            TenantService.MAIN_TENANT, namespace, nsFileStorageUri, new StorageObject(
                null,
                new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))
            )
        );
    }

    private static @NonNull URI getNsFileStorageUri(String namespace, String path) {
        return URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.namespaceFilePrefix(namespace) + path);
    }
}

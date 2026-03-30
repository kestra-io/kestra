package io.kestra.cli.commands.migrations.metadata;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.contexts.KestraConfig;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;

public class MetadataMigrationServiceTest<T extends MetadataMigrationService> {

    private static final String TENANT_ID = TestsUtils.randomTenant();
    protected static final String SYSTEM_NAMESPACE = "my.system.namespace";

    @Test
    void namespacesPerTenant() {
        Map<String, List<String>> expected = getNamespacesPerTenant();
        Map<String, List<String>> result = metadataMigrationService(expected).namespacesPerTenant();
        
        assertThat(result).hasSize(expected.size());
        expected.forEach((tenantId, namespaces) ->
        {
            assertThat(result.get(tenantId)).containsExactlyInAnyOrderElementsOf(
                    Stream.concat(Stream.of(SYSTEM_NAMESPACE), namespaces.stream())
                            .map(NamespaceInterface::asTree)
                            .flatMap(Collection::stream)
                            .distinct()
                            .toList()
            );
        });
    }

    @Test
    void shouldNotMigrateRevisionFiles() throws Exception {

        StorageInterface storageInterface = Mockito.mock(StorageInterface.class);
        NamespaceFileMetadataRepositoryInterface repo = Mockito.mock(NamespaceFileMetadataRepositoryInterface.class);

        FlowRepositoryInterface flowRepository = Mockito.mock(FlowRepositoryInterface.class);
        Mockito.when(flowRepository.findDistinctNamespace(Mockito.anyString()))
                .thenReturn(getNamespacesPerTenant().get(TENANT_ID));

        KestraConfig kestraConfig = Mockito.mock(KestraConfig.class);
        Mockito.when(kestraConfig.getSystemFlowNamespace()).thenReturn(SYSTEM_NAMESPACE);
        KvMetadataRepositoryInterface kvRepo = Mockito.mock(KvMetadataRepositoryInterface.class);
        MetadataMigrationService service = new MetadataMigrationService(
                flowRepository,
                new TenantService() {
                    @Override
                    public String resolveTenant() {
                        return TENANT_ID;
                    }
                },
                repo, // NamespaceFileMetadataRepository
                storageInterface, // StorageInterface
                kestraConfig, // KestraConfig
                kvRepo // KvMetadataRepository
        );
        String namespace = "io.kestra.test";
        String prefix = StorageContext.namespaceFilePrefix(namespace);

        URI canonical = URI.create("kestra://" + prefix + "/test.yaml");
        URI revision = URI.create("kestra://" + prefix + "/test.yaml.v1");

        Mockito.when(storageInterface.allByPrefix(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(true)))
                .thenReturn(List.of(canonical, revision));

        FileAttributes attributes = Mockito.mock(FileAttributes.class);
        Mockito.when(attributes.getSize()).thenReturn(100L);

        Mockito.when(storageInterface.getAttributes(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(attributes);

        service.nsFilesMigration(false);

        Mockito.verify(repo, never()).save(Mockito.argThat(meta -> meta.getPath().endsWith(".v1")));
    }

    protected Map<String, List<String>> getNamespacesPerTenant() {
        return Map.of(TENANT_ID, List.of("my.first.namespace", "my.second.namespace", "another.namespace"));
    }

    protected T metadataMigrationService(Map<String, List<String>> namespacesPerTenant) {
        FlowRepositoryInterface mockedFlowRepository = Mockito.mock(FlowRepositoryInterface.class);
        Mockito.doAnswer((params) -> namespacesPerTenant.get(params.getArgument(0).toString()))
                .when(mockedFlowRepository)
                .findDistinctNamespace(Mockito.anyString());

        KestraConfig kestraConfig = Mockito.mock(KestraConfig.class);
        Mockito.when(kestraConfig.getSystemFlowNamespace()).thenReturn(SYSTEM_NAMESPACE);
        KvMetadataRepositoryInterface kvRepo = Mockito.mock(KvMetadataRepositoryInterface.class);
        return ((T) new MetadataMigrationService(
                mockedFlowRepository,
                new TenantService() {
                    @Override
                    public String resolveTenant() {
                        return TENANT_ID;
                    }
                },
                null, // namespaceFileMetadataRepository
                null, // storageInterface
                kestraConfig, // config
                kvRepo // kv repo
        ));
    }
}
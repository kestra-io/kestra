package io.kestra.cli.commands.migrations.metadata;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.core.utils.TestsUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class MetadataMigrationServiceTest<T extends MetadataMigrationService> {
    private static final String TENANT_ID = TestsUtils.randomTenant();

    protected static final String SYSTEM_NAMESPACE = "my.system.namespace";

    @Test
    void namespacesPerTenant() {
        Map<String, List<String>> expected = getNamespacesPerTenant();
        Map<String, List<String>> result = metadataMigrationService(
            expected
        ).namespacesPerTenant();

        assertThat(result).hasSize(expected.size());
        expected.forEach((tenantId, namespaces) -> {
            assertThat(result.get(tenantId)).containsExactlyInAnyOrderElementsOf(
                Stream.concat(
                    Stream.of(SYSTEM_NAMESPACE),
                    namespaces.stream()
                ).map(NamespaceUtils::asTree).flatMap(Collection::stream).distinct().toList()
            );
        });
    }

    protected Map<String, List<String>> getNamespacesPerTenant() {
        return Map.of(TENANT_ID, List.of("my.first.namespace", "my.second.namespace", "another.namespace"));
    }

    @Test
    void shouldNotMigrateRevisionFiles() throws Exception {
        NamespaceFileMetadataRepositoryInterface repo = Mockito.mock(NamespaceFileMetadataRepositoryInterface.class);
        StorageInterface storage = Mockito.mock(StorageInterface.class);

        String namespace = "test.namespace";

        MetadataMigrationService service = new MetadataMigrationService(
            Mockito.mock(FlowRepositoryInterface.class),
            new TenantService() {
                @Override
                public String resolveTenant() {
                    return TENANT_ID;
                }
            },
            null,
            repo,
            storage,
            Mockito.mock(NamespaceUtils.class)
        ) {
            @Override
            public Map<String, List<String>> namespacesPerTenant() {
                return Map.of(TENANT_ID, List.of(namespace));
            }
        };

        URI normalUri = URI.create("kestra://namespace/test.namespace/file.yaml");
        URI revisionUri = URI.create("kestra://namespace/test.namespace/file.yaml.v1");

        Mockito.doReturn(List.of(normalUri, revisionUri))
            .when(storage)
            .allByPrefix(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        FileAttributes attributes = Mockito.mock(FileAttributes.class);

        Mockito.when(storage.getAttributes(Mockito.any(), Mockito.any(), Mockito.eq(normalUri)))
            .thenReturn(attributes);

        Mockito.when(storage.getAttributes(Mockito.any(), Mockito.any(), Mockito.eq(revisionUri)))
            .thenReturn(attributes);

        Mockito.when(repo.findByPath(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(Optional.empty());

        service.nsFilesMigration(false);

        Mockito.verify(repo, Mockito.never())
            .save(Mockito.argThat(meta -> meta.getPath().endsWith(".v1")));

        Mockito.verify(repo)
            .save(Mockito.argThat(meta -> !meta.getPath().endsWith(".v1")));
    }

    protected T metadataMigrationService(Map<String, List<String>> namespacesPerTenant) {
        FlowRepositoryInterface mockedFlowRepository = Mockito.mock(FlowRepositoryInterface.class);
        Mockito.doAnswer((params) -> namespacesPerTenant.get(params.getArgument(0).toString())).when(mockedFlowRepository).findDistinctNamespace(Mockito.anyString());
        NamespaceUtils namespaceUtils = Mockito.mock(NamespaceUtils.class);
        Mockito.when(namespaceUtils.getSystemFlowNamespace()).thenReturn(SYSTEM_NAMESPACE);
        //noinspection unchecked
        return ((T) new MetadataMigrationService(mockedFlowRepository, new TenantService() {
            @Override
            public String resolveTenant() {
                return TENANT_ID;
            }
        }, null, null, null, namespaceUtils));
    }
}

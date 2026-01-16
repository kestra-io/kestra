package io.kestra.cli.commands.migrations.metadata;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.core.utils.TestsUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

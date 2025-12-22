package io.kestra.core.utils;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class UriProviderTest {
    @Inject
    UriProvider uriProvider;

    @Test
    void root() {
        assertThat(uriProvider.rootUrl().toString()).contains("mysuperhost.com/subpath/");
    }

    @Test
    void flowUrl() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        assertThat(uriProvider.executionUrl(execution).toString()).contains("mysuperhost.com/subpath/ui");
        assertThat(uriProvider.flowUrl(execution).toString()).contains(flow.getNamespace() + "/" + flow.getId());

        assertThat(uriProvider.executionUrl(execution).toString()).contains("mysuperhost.com/subpath/ui");
        assertThat(uriProvider.flowUrl(flow).toString()).contains(flow.getNamespace() + "/" + flow.getId());
    }

    @Test
    void executionUrl() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        assertThat(uriProvider.executionUrl(execution).toString()).contains("mysuperhost.com/subpath/ui");
        assertThat(uriProvider.executionUrl(execution).toString()).contains(flow.getNamespace() + "/" + flow.getId() + "/" + execution.getId());
    }

    @Test
    void tenant() {
        Flow flow = TestsUtils.mockFlow()
            .toBuilder()
            .tenantId("my-tenant")
            .build();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        assertThat(uriProvider.executionUrl(execution).toString()).contains("mysuperhost.com/subpath/ui/my-tenant");
        assertThat(uriProvider.flowUrl(flow).toString()).contains("mysuperhost.com/subpath/ui/my-tenant");
    }
}
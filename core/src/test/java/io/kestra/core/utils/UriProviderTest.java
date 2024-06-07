package io.kestra.core.utils;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
class UriProviderTest {
    @Inject
    UriProvider uriProvider;

    @Test
    void root() {
        assertThat(uriProvider.rootUrl().toString(), containsString("mysuperhost.com/subpath/"));
    }

    @Test
    void flowUrl() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        assertThat(uriProvider.executionUrl(execution).toString(), containsString("mysuperhost.com/subpath/ui"));
        assertThat(uriProvider.flowUrl(execution).toString(), containsString(flow.getNamespace() + "/" + flow.getId()));

        assertThat(uriProvider.executionUrl(execution).toString(), containsString("mysuperhost.com/subpath/ui"));
        assertThat(uriProvider.flowUrl(flow).toString(), containsString(flow.getNamespace() + "/" + flow.getId()));
    }

    @Test
    void executionUrl() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        assertThat(uriProvider.executionUrl(execution).toString(), containsString("mysuperhost.com/subpath/ui"));
        assertThat(uriProvider.executionUrl(execution).toString(), containsString(flow.getNamespace() + "/" + flow.getId() + "/" + execution.getId()));
    }

    @Test
    void tenant() {
        Flow flow = TestsUtils.mockFlow()
            .toBuilder()
            .tenantId("my-tenant")
            .build();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        assertThat(uriProvider.executionUrl(execution).toString(), containsString("mysuperhost.com/subpath/ui/my-tenant"));
        assertThat(uriProvider.flowUrl(flow).toString(), containsString("mysuperhost.com/subpath/ui/my-tenant"));
    }
}
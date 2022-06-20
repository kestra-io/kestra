package io.kestra.core.services;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.Helpers;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.Usage;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class CollectorServiceTest {
    @Test
    public void metrics() throws URISyntaxException {
        ImmutableMap<String, Object> properties = ImmutableMap.of("kestra.server-type", ServerType.WEBSERVER.name());

        try (ApplicationContext applicationContext = Helpers.applicationContext(properties).start()) {
            CollectorService collectorService = applicationContext.getBean(CollectorService.class);
            Usage metrics = collectorService.metrics();

            assertThat(metrics.getUri(), is("https://mysuperhost.com/subpath"));

            assertThat(metrics.getUuid(), notNullValue());
            assertThat(metrics.getVersion(), notNullValue());
            assertThat(metrics.getStartTime(), notNullValue());
            assertThat(metrics.getEnvironments(), contains("test"));
            assertThat(metrics.getStartTime(), notNullValue());
            assertThat(metrics.getHost().getUuid(), notNullValue());
            assertThat(metrics.getHost().getHardware().getLogicalProcessorCount(), notNullValue());
            assertThat(metrics.getHost().getJvm().getName(), notNullValue());
            assertThat(metrics.getHost().getOs().getFamily(), notNullValue());
            assertThat(metrics.getConfigurations().getRepositoryType(), is("local"));
            assertThat(metrics.getConfigurations().getQueueType(), is("memory"));
        }
    }
}
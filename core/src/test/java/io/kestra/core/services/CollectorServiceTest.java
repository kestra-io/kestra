package io.kestra.core.services;

import io.kestra.core.Helpers;
import io.kestra.core.models.collectors.Metrics;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class CollectorServiceTest {
    @Test
    public void metrics() throws URISyntaxException {
        try (ApplicationContext applicationContext = Helpers.applicationContext().start()) {
            CollectorService collectorService = applicationContext.getBean(CollectorService.class);
            Metrics metrics = collectorService.metrics();

            assertThat(metrics.getUri(), is(IdUtils.from("https://mysuperhost.com/subpath")));

            assertThat(metrics.getUuid(), notNullValue());
            assertThat(metrics.getVersion(), notNullValue());
            assertThat(metrics.getStartTime(), notNullValue());
            assertThat(metrics.getEnvironments(), contains("test"));
            assertThat(metrics.getStartTime(), notNullValue());
            assertThat(metrics.getHost().getUuid(), notNullValue());
            assertThat(metrics.getHost().getHardware().getLogicalProcessorCount(), notNullValue());
            assertThat(metrics.getHost().getJvm().getName(), notNullValue());
            assertThat(metrics.getHost().getOs().getFamily(), notNullValue());
        }
    }
}
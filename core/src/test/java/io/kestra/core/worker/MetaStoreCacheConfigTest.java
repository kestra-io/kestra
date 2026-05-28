package io.kestra.core.worker;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaStoreCacheConfigTest {

    @Test
    void shouldApplyDefaultsWhenNothingConfigured() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            MetaStoreCacheConfig config = ctx.getBean(MetaStoreCacheConfig.class);

            assertThat(config.maximumSize()).isEqualTo(10_000L);
            assertThat(config.expireAfterAccess()).isEqualTo(Duration.ofHours(1));
        }
    }

    @Test
    void shouldOverrideFromProperties() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "kestra.worker.metastore-cache.maximum-size", "500",
            "kestra.worker.metastore-cache.expire-after-access", "PT10M"
        ))) {
            MetaStoreCacheConfig config = ctx.getBean(MetaStoreCacheConfig.class);

            assertThat(config.maximumSize()).isEqualTo(500L);
            assertThat(config.expireAfterAccess()).isEqualTo(Duration.ofMinutes(10));
        }
    }
}

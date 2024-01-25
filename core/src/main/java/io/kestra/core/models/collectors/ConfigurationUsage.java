package io.kestra.core.models.collectors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@SuperBuilder
@Getter
@Jacksonized
@Introspected
public class ConfigurationUsage {
    private final String repositoryType;
    private final String queueType;
    private final String storageType;
    private final String secretType;
    private final Boolean javaSecurityEnabled;

    // TODO: Once kestra-io/kestra-ee#588 is done, we should target the proper property instead
    public static ConfigurationUsage of(String tenantId, ApplicationContext applicationContext) {
        return ConfigurationUsage.of(applicationContext);
    }

    public static ConfigurationUsage of(ApplicationContext applicationContext) {
        return ConfigurationUsage.builder()
            .repositoryType(applicationContext.getProperty("kestra.repository.type", String.class).orElse(null))
            .queueType(applicationContext.getProperty("kestra.queue.type", String.class).orElse(null))
            .storageType(applicationContext.getProperty("kestra.storage.type", String.class).orElse(null))
            .secretType(applicationContext.getProperty("kestra.secret.type", String.class).orElse(null))
            .javaSecurityEnabled(applicationContext.getProperty("kestra.ee.java-security.enabled", Boolean.class).orElse(null))
            .build();
    }
}

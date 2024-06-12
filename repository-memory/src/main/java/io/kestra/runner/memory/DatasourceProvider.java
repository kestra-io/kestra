package io.kestra.runner.memory;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.flyway.FlywayConfigurationProperties;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Optional;

@Factory
@Requires(missingProperty = "datasources", condition = DatasourceProvider.H2RepositoryOrQueue.class)
public class DatasourceProvider {
    @Singleton
    @Named("h2")
    public CustomDatasourceConfiguration getDatasourceConfiguration() {
        CustomDatasourceConfiguration memory = new CustomDatasourceConfiguration("h2");
        memory.setUrl("jdbc:h2:mem:public");
        memory.setUsername("sa");
        memory.setPassword("");
        memory.setDriverClassName("org.h2.Driver");
        return memory;
    }

    @Singleton
    @Named("h2")
    public CustomFlywayConfiguration getFlywayConfiguration() {
        CustomFlywayConfiguration flyway = new CustomFlywayConfiguration("h2");
        flyway.setEnabled(true);
        flyway.setLocations("classpath:migrations/h2");
        flyway.setIgnoreMigrationPatterns("*:missing","*:future");
        flyway.getProperties().put("outOfOrder", "true");
        return flyway;
    }

    // We have to create an extended class to be able to create bean from it as DatasourceConfiguration can only be configured as yaml properties
    public static class CustomDatasourceConfiguration extends DatasourceConfiguration {
        public CustomDatasourceConfiguration(String name) {
            super(name);
        }
    }

    // We have to create an extended class to be able to create bean from it as DatasourceConfiguration can only be configured as yaml properties
    public static class CustomFlywayConfiguration extends FlywayConfigurationProperties {
        public CustomFlywayConfiguration(String name) {
            super(name);
        }
    }

    public static class H2RepositoryOrQueue implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            Optional<String> repositoryType = context.getProperty("kestra.repository.type", String.class);
            if (repositoryType.isPresent() && (repositoryType.get().equals("h2") || repositoryType.get().equals("memory"))) {
                return true;
            }

            Optional<String> queueType = context.getProperty("kestra.queue.type", String.class);
            return queueType.isPresent() && (queueType.get().equals("h2") || queueType.get().equals("memory"));
        }
    }
}

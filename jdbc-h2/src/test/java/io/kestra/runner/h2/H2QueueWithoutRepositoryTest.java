package io.kestra.runner.h2;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.conf.Settings;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.kestra.jdbc.runner.QueueJdbcConfiguration;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import io.kestra.repository.h2.H2Repository;
import io.kestra.repository.h2.migration.V2_0QueueMigration;
import io.kestra.repository.h2.migration.V2_0_02QueueMigration;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;

import static org.assertj.core.api.Assertions.assertThat;

class H2QueueWithoutRepositoryTest {
    @Test
    void shouldPublishAndConsumeWhenNoH2RepositoryIsRegistered() throws Exception {
        // Given: only the queue uses H2; the primary repository is a different backend.
        try (
            var provider = new QueueJdbcDataSourceProvider(
                new QueueJdbcConfiguration(
                    Map.of(
                        "type", "h2", "url", "jdbc:h2:mem:queue-without-repository", "username", "sa"
                    )
                ),
                new Settings(), null, null, new RepositoryConfiguration("postgres"), ""
            );
            var context = ApplicationContext.builder()
                .deduceEnvironment(false)
                .beansPredicate(bean -> !bean.getAnnotationMetadata().hasStereotype(Context.class))
                .properties(
                    Map.of(
                        "kestra.repository.type", "postgres",
                        "kestra.queue.type", "h2"
                    )
                )
                .singletons(provider)
                .start()
        ) {
            context.getBean(V2_0QueueMigration.class).migrate();
            context.getBean(V2_0_02QueueMigration.class).migrate();
            assertThat(context.getBeansOfType(H2Repository.class)).isEmpty();
            var client = context.getBean(JdbcQueueClient.class);
            List<String> received = new ArrayList<>();

            // When
            client.publish("test", "worker", "key", "{\"value\":1}");
            assertThat(client.queueLag("test", "worker")).isEqualTo(1);
            int consumed = client.subscribeDispatch(
                "test", List.of("worker"),
                message -> received.add(new String(message, StandardCharsets.UTF_8))
            );

            // Then
            assertThat(consumed).isEqualTo(1);
            assertThat(received).containsExactly("{\"value\":1}");
            assertThat(client.queueLag("test", "worker")).isZero();
        }
    }
}

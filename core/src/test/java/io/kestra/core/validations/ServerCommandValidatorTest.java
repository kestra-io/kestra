package io.kestra.core.validations;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.ServerType;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.NoSuchBeanException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerCommandValidatorTest {

    @Test
    void noServerCommandIssued() {
        // deduceEnvironment(false) prevents picking up the "test" environment
        // (and its application-test.yml which sets kestra.server-type), so we
        // can verify the validator is genuinely absent when no server command
        // is issued.
        try (
            ApplicationContext context = ApplicationContext.builder()
                .deduceEnvironment(false)
                .start()
        ) {
            Assertions.assertThrows(NoSuchBeanException.class, () -> context.getBean(ServerCommandValidator.class));
        }
    }

    @Test
    void serverCommandIssued() {
        Assertions.assertDoesNotThrow(
            () -> ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("test")
                .properties(
                    Map.of(
                        "kestra.server-type", "webserver",
                        "kestra.queue.type", "memory",
                        "kestra.repository.type", "memory",
                        "kestra.storage.type", "local"
                    )
                )
                .start()
        );

        final Throwable exception = Assertions.assertThrows(
            BeanInstantiationException.class, () -> ApplicationContext.builder()
                .deduceEnvironment(false)
                .properties(
                    Map.of(
                        "kestra.server-type", "webserver",
                        "kestra.repository.type", "h2",
                        "kestra.queue.type", "h2",
                        "datasources.h2.url", "jdbc:h2:mem:test-cmd-validator;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "datasources.h2.username", "sa",
                        "datasources.h2.password", "",
                        "datasources.h2.driverClassName", "org.h2.Driver"
                    )
                )
                .start()
        );
        final Throwable rootException = getRootException(exception);
        assertThat(rootException.getClass()).isEqualTo(ServerCommandValidator.ServerCommandException.class);
        assertThat(rootException.getMessage()).isEqualTo("Incomplete server configuration - missing required properties");
    }

    private Throwable getRootException(Throwable exception) {
        while (exception.getCause() != null) {
            exception = exception.getCause();
        }
        return exception;
    }

    @Test
    void shouldReturnValidResultsWhenAllRequiredPropertiesPresent() {
        Environment environment = mock(Environment.class);
        when(environment.containsProperty("kestra.queue.type")).thenReturn(true);
        when(environment.containsProperty("kestra.repository.type")).thenReturn(true);
        when(environment.containsProperty("kestra.storage.type")).thenReturn(true);

        List<ConfigValidationResult> results = ServerCommandValidator.validateServerConfiguration(environment, ServerType.WEBSERVER);

        assertThat(results)
            .as("All required webserver properties present is valid")
            .isNotEmpty()
            .allMatch(ConfigValidationResult::valid);
    }

    @Test
    void shouldReturnInvalidResultWhenRequiredPropertyMissing() {
        Environment environment = mock(Environment.class);
        when(environment.containsProperty("kestra.queue.type")).thenReturn(true);
        when(environment.containsProperty("kestra.repository.type")).thenReturn(true);
        when(environment.containsProperty("kestra.storage.type")).thenReturn(false);

        List<ConfigValidationResult> results = ServerCommandValidator.validateServerConfiguration(environment, ServerType.WEBSERVER);

        assertThat(results)
            .as("A missing kestra.storage.type is reported as invalid with a message")
            .anyMatch(result -> !result.valid() && result.key().equals("kestra.storage.type") && result.message() != null);
    }

    @Test
    void shouldReportDatabasePropertiesAsIgnoredForWorker() {
        // Given a shared configuration carrying the database settings of the other server types.
        try (
            ApplicationContext context = ApplicationContext.builder()
                .deduceEnvironment(false)
                .properties(
                    Map.of(
                        "kestra.server-type", "worker",
                        "kestra.storage.type", "local",
                        "kestra.repository.type", "h2",
                        "datasources.h2.url", "jdbc:h2:mem:test-worker-ignored;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "datasources.h2.username", "sa",
                        "datasources.h2.password", "",
                        "datasources.h2.driverClassName", "org.h2.Driver"
                    )
                )
                .start()
        ) {
            Environment environment = context.getEnvironment();

            assertThat(ServerCommandValidator.ignoredWorkerProperties(environment, ServerType.WORKER))
                .as("A worker uses no database, so both the datasources block and the repository type are ignored")
                .containsExactly("datasources", "kestra.repository.type");

            assertThat(ServerCommandValidator.ignoredWorkerProperties(environment, ServerType.WEBSERVER))
                .as("Every other server type does use them")
                .isEmpty();
        }
    }

    @Test
    void shouldOnlyRequireStorageForWorker() {
        Environment environment = mock(Environment.class);
        when(environment.containsProperty("kestra.storage.type")).thenReturn(true);

        List<ConfigValidationResult> results = ServerCommandValidator.validateServerConfiguration(environment, ServerType.WORKER);

        assertThat(results)
            .as("A worker only requires kestra.storage.type")
            .hasSize(1)
            .allMatch(ConfigValidationResult::valid);
    }
}
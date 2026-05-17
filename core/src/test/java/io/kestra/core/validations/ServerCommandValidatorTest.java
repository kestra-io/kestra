package io.kestra.core.validations;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.NoSuchBeanException;

import static org.assertj.core.api.Assertions.assertThat;

class ServerCommandValidatorTest {

    @Test
    void noServerCommandIssued() {
        // deduceEnvironment(false) prevents picking up the "test" environment
        // (and its application-test.yml which sets kestra.server-type), so we
        // can verify the validator is genuinely absent when no server command
        // is issued.
        try (ApplicationContext context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .start()) {
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
}
package io.kestra.cli;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ServerCommandValidatorTest {

    @Test
    void noServerCommandIssued() {
        try (ApplicationContext context = ApplicationContext.run()) {
            Assertions.assertThrows(NoSuchBeanException.class, () -> context.getBean(ServerCommandValidator.class));
        }
    }

    @Test
    void serverCommandIssued() {
        Assertions.assertDoesNotThrow(() -> ApplicationContext.builder()
            .deduceEnvironment(false)
            .properties(Map.of(
                "kestra.server-type", "webserver",
                "kestra.queue.type", "memory",
                "kestra.repository.type", "memory",
                "kestra.storage.type", "local"
            ))
            .start()
        );

        final Throwable exception = Assertions.assertThrows(BeanInstantiationException.class, () ->
            ApplicationContext.builder()
                .deduceEnvironment(false)
                .properties(Map.of("kestra.server-type", "webserver"))
                .start()
        );
        final Throwable rootException = getRootException(exception);
        assertThat(rootException.getClass(), is(ServerCommandValidator.ServerCommandException.class));
        assertThat(rootException.getMessage(), is("Incomplete server configuration - missing required properties"));
    }

    private Throwable getRootException(Throwable exception) {
        while (exception.getCause() != null) {
            exception = exception.getCause();
        }
        return exception;
    }
}
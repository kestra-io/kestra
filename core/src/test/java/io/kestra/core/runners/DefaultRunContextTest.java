package io.kestra.core.runners;

import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class DefaultRunContextTest {

    @Inject
    private ApplicationContext applicationContext;

    @Value("${kestra.encryption.secret-key}")
    private String secretKey;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldGetKestraVersion() {
        DefaultRunContext runContext = new DefaultRunContext();
        runContext.init(applicationContext);
        Assertions.assertNotNull(runContext.version());
    }

    @Test
    void shouldDecryptVariables() throws GeneralSecurityException, IllegalVariableEvaluationException {
        RunContext runContext = runContextFactory.of();

        String encryptedSecret = EncryptionService.encrypt(secretKey, "It's a secret");
        Map<String, Object> variables = Map.of("test", "test",
            "secret", Map.of("type", EncryptedString.TYPE, "value", encryptedSecret));

        String render = runContext.render("What ? {{secret}}", variables);
        assertThat(render, is(("What ? It's a secret")));
    }
}
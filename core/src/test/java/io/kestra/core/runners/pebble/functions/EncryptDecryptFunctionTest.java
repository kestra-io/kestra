package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
class EncryptDecryptFunctionTest {
    @Inject
    private VariableRenderer variableRenderer;

    @Value("${kestra.encryption.secret-key}")
    private String secretKey;

    @Test
    void missingParameter() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{encrypt('toto')}}", Collections.emptyMap())
        );

        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{decrypt('toto')}}", Collections.emptyMap())
        );
    }

    @Test
    void encryptDecrypt() throws IllegalVariableEvaluationException {
        String encrypted = variableRenderer.render("{{encrypt(secretKey, 'toto')}}", Map.of("secretKey", secretKey));
        assertThat(encrypted, notNullValue());

        String decrypted = variableRenderer.render("{{decrypt(secretKey, '" + encrypted + "')}}", Map.of("secretKey", secretKey));
        assertThat(decrypted, is("toto"));
    }
}
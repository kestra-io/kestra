package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
class EncryptDecryptFunctionTest {
    @Inject
    private VariableRenderer variableRenderer;

    @Test
    void notConfigured() {
        assertThrows(
            IllegalArgumentException.class,
            () -> variableRenderer.render("{{encrypt('toto')}}", Collections.emptyMap())
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> variableRenderer.render("{{decrypt('toto')}}", Collections.emptyMap())
        );
    }

    @Test
    @Property(name = "kestra.crypto.secret-key", value = "I6EGNzRESu3X3pKZidrqCGOHQFUFC0yK")
    void encryptDecrypt() throws IllegalVariableEvaluationException {
        String encrypted = variableRenderer.render("{{encrypt('toto')}}", Collections.emptyMap());
        assertThat(encrypted, notNullValue());

        String decrypted = variableRenderer.render("{{decrypt('" + encrypted + "')}}", Collections.emptyMap());
        assertThat(decrypted, is("toto"));
    }
}
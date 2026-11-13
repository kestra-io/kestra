package io.kestra.core.secret;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "kestra.secret.encoding", value = "RAW")
class SecretServiceRawEncodingTest {

    @Inject
    private SecretService secretService;

    @Test
    @EnabledIfEnvironmentVariable(named = "SECRET_MY_SECRET", matches = ".*")
    void shouldNotDecodeSecretWhenEncodingIsRaw() throws SecretNotFoundException, IOException {
        // Given the SECRET_MY_SECRET environment variable holds a Base64-encoded value

        // When
        String secret = secretService.findSecret(MAIN_TENANT, "io.kestra.tests", "my_secret");

        // Then
        assertThat(secret).isEqualTo(System.getenv("SECRET_MY_SECRET"));
    }
}

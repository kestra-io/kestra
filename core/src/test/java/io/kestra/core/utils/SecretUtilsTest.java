package io.kestra.core.utils;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecretUtilsTest {

    static class TaskWithSecretString {
        @PluginProperty(secret = true)
        String password;
    }

    static class TaskWithSecretProperty {
        @PluginProperty(secret = true)
        Property<String> apiKey;
    }

    static class TaskWithNonSecret {
        @PluginProperty
        String username;
    }

    static class TaskWithNullSecret {
        @PluginProperty(secret = true)
        String token;
    }

    @Test
    void plainTextStringViolates() {
        TaskWithSecretString task = new TaskWithSecretString();
        task.password = "my-plain-text-password";

        List<String> violations = SecretUtils.validateSecretFields(task);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst()).contains("password");
    }

    @Test
    void pebbleExpressionStringPasses() {
        TaskWithSecretString task = new TaskWithSecretString();
        task.password = "{{ secret('MY_PASSWORD') }}";

        List<String> violations = SecretUtils.validateSecretFields(task);

        assertThat(violations).isEmpty();
    }

    @Test
    void plainTextPropertyViolates() {
        TaskWithSecretProperty task = new TaskWithSecretProperty();
        task.apiKey = Property.ofValue("plain-api-key");

        List<String> violations = SecretUtils.validateSecretFields(task);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst()).contains("apiKey");
    }

    @Test
    void pebbleExpressionPropertyPasses() {
        TaskWithSecretProperty task = new TaskWithSecretProperty();
        task.apiKey = Property.ofExpression("{{ secret('MY_API_KEY') }}");

        List<String> violations = SecretUtils.validateSecretFields(task);

        assertThat(violations).isEmpty();
    }

    @Test
    void nullValueIsSkipped() {
        TaskWithNullSecret task = new TaskWithNullSecret();
        // token is null

        List<String> violations = SecretUtils.validateSecretFields(task);

        assertThat(violations).isEmpty();
    }

    @Test
    void nonSecretFieldIsIgnored() {
        TaskWithNonSecret task = new TaskWithNonSecret();
        task.username = "john";

        List<String> violations = SecretUtils.validateSecretFields(task);

        assertThat(violations).isEmpty();
    }

    @Test
    void nullObjectReturnsEmpty() {
        List<String> violations = SecretUtils.validateSecretFields(null);

        assertThat(violations).isEmpty();
    }
}

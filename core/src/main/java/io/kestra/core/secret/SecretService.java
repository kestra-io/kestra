package io.kestra.core.secret;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import jakarta.inject.Singleton;

import java.util.Base64;
import java.util.Optional;

@Singleton
public class SecretService {
    public String findSecret(String key) throws IllegalVariableEvaluationException {
        String environmentVariable = Optional.ofNullable(System.getenv("SECRETS_" + key.toUpperCase()))
            .orElseThrow(() -> new IllegalVariableEvaluationException("Unable to find secret '" + key + "'. " +
                "You should add it in your environment variables as 'SECRETS_" + key.toUpperCase()+"' with base64-encoded value."));
        return new String(Base64.getDecoder().decode(environmentVariable));
    }
}

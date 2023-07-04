package io.kestra.core.secret;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import jakarta.inject.Singleton;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class SecretService {
    private static final String SECRET_PREFIX = "SECRETS_";
    private Map<String, String> decodedSecrets;

    @PostConstruct
    private void postConstruct() {
        decodedSecrets = System.getenv().entrySet().stream().filter(entry -> entry.getKey().startsWith(SECRET_PREFIX)).collect(Collectors.toMap(
            entry -> entry.getKey().substring(SECRET_PREFIX.length()).toUpperCase(),
            entry -> new String(Base64.getDecoder().decode(entry.getValue()))
        ));
    }

    public String findSecret(String namespace, String key) throws IOException, IllegalVariableEvaluationException {
        return Optional
            .ofNullable(decodedSecrets.get(key.toUpperCase()))
            .orElseThrow(() -> new IllegalVariableEvaluationException("Unable to find secret '" + key + "'. " +
                "You should add it in your environment variables as 'SECRETS_" + key.toUpperCase() +
                "' with base64-encoded value."
            ));
    }
}

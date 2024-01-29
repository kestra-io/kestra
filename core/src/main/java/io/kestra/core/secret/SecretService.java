package io.kestra.core.secret;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class SecretService {
    private static final String SECRET_PREFIX = "SECRET_";
    private Map<String, String> decodedSecrets;

    @PostConstruct
    private void postConstruct() {
        decodedSecrets = System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(SECRET_PREFIX))
            .<Map.Entry<String, String>>mapMulti((entry, consumer) -> {
                try {
                    consumer.accept(Map.entry(entry.getKey(), new String(Base64.getDecoder().decode(entry.getValue()))));
                } catch (Exception e) {
                    log.error("Could not decode secret '{}', make sure it is Base64-encoded: {}", entry.getKey(), e.getMessage());
                }
            })
            .collect(Collectors.toMap(
                entry -> entry.getKey().substring(SECRET_PREFIX.length()).toUpperCase(),
                Map.Entry::getValue
            ));
    }

    public String findSecret(String tenantId, String namespace, String key) throws IOException, IllegalVariableEvaluationException {
        return Optional
            .ofNullable(decodedSecrets.get(key.toUpperCase()))
            .orElseThrow(() -> new IllegalVariableEvaluationException("Unable to find secret '" + key + "'. " +
                "You should add it in your environment variables as '" + SECRET_PREFIX + key.toUpperCase() +
                "' with base64-encoded value."
            ));
    }
}

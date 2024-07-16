package io.kestra.core.secret;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
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
                    String value = entry.getValue().replaceAll("\\R", "");
                    consumer.accept(Map.entry(entry.getKey(), new String(Base64.getDecoder().decode(value))));
                } catch (Exception e) {
                    log.error("Could not decode secret '{}', make sure it is Base64-encoded: {}", entry.getKey(), e.getMessage());
                }
            })
            .collect(Collectors.toMap(
                entry -> entry.getKey().substring(SECRET_PREFIX.length()).toUpperCase(),
                Map.Entry::getValue
            ));
    }

    public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        return decodedSecrets.get(key.toUpperCase());
    }
}

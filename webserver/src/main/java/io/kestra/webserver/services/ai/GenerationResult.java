package io.kestra.webserver.services.ai;

import java.util.Optional;

public record GenerationResult(String content, Optional<Integer> remainingQuota) {
    public static GenerationResult of(String content) {
        return new GenerationResult(content, Optional.empty());
    }
}

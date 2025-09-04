package io.kestra.webserver.services.ai;

public interface AiConfiguration {
    String modelName();
    default Double temperature() {
        return 0.7;
    }
    default Double topP() {
        return null;
    }
}

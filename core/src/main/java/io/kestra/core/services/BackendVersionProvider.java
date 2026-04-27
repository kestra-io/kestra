package io.kestra.core.services;

import io.micronaut.core.annotation.Nullable;

/**
 * Provides the version of a backend service (e.g., Elasticsearch, Kafka, RabbitMQ, Redis).
 * <p>
 * Implementations are discovered at runtime via Micronaut dependency injection.
 * Each implementation should be activated only when the corresponding backend is configured
 * (e.g., via {@code @Requires} annotations).
 */
public interface BackendVersionProvider {

    /**
     * The category of a backend service.
     */
    enum Category {
        REPOSITORY,
        QUEUE
    }

    /**
     * The backend type matching the configuration property value
     * (e.g., {@code "elasticsearch"}, {@code "kafka"}, {@code "amqp"}, {@code "redis"}).
     *
     * @return the backend type identifier
     */
    String type();

    /**
     * The category of backend.
     *
     * @return the backend category
     */
    Category category();

    /**
     * Returns the backend service version string (e.g., {@code "Elasticsearch 8.12.0"}, {@code "Kafka 3.6"}).
     *
     * @return the version string, or {@code null} if the version cannot be determined
     */
    @Nullable
    String getVersion();
}

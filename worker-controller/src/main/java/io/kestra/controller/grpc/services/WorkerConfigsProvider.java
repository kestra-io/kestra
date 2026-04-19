package io.kestra.controller.grpc.services;

import java.util.Map;

/**
 * Provides configuration to be sent to workers during the connect handshake.
 * <p>
 * The returned map is serialized and included in the {@code worker_configs} field of the
 * {@code ConnectResponse} proto message. EE modules can replace the default
 * implementation to add license-dependent configuration.
 */
public interface WorkerConfigsProvider {

    /**
     * Returns the configuration map to propagate to the connecting worker.
     *
     * @return an unmodifiable map of configuration key-value pairs
     */
    Map<String, Object> get();
}

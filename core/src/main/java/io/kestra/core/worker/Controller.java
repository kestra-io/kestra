package io.kestra.core.worker;

import io.kestra.core.server.Service;

/**
 * Interface representing a controller service in the system.
 * A controller is responsible for managing and coordinating worker nodes.
 */
public interface Controller extends Service {

    /**
     * Starts the controller service.
     */
    void start();
}

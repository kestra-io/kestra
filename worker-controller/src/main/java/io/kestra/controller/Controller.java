package io.kestra.controller;

import io.kestra.core.server.Service;

import java.io.IOException;

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

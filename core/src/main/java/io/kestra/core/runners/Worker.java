package io.kestra.core.runners;

import io.kestra.core.server.Service;

public interface Worker extends Service, Runnable {
    String EXECUTOR_NAME = "worker";
}

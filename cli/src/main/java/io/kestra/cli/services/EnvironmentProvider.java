package io.kestra.cli.services;

public interface EnvironmentProvider {
    String[] getCliEnvironments(String... extraEnvironments);
}

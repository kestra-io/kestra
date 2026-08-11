package io.kestra.cli.services;

import java.util.Arrays;
import java.util.stream.Stream;

import io.micronaut.context.env.Environment;

public class DefaultEnvironmentProvider implements EnvironmentProvider {
    @Override
    public String[] getCliEnvironments(String... extraEnvironments) {
        return Stream.concat(
            Stream.of(Environment.CLI),
            Arrays.stream(extraEnvironments)
        ).toArray(String[]::new);
    }
}

package io.kestra.cli.services;

import io.micronaut.context.env.Environment;

import java.util.Arrays;
import java.util.stream.Stream;

public class DefaultEnvironmentProvider implements EnvironmentProvider {
    @Override
    public String[] getCliEnvironments(String... extraEnvironments) {
        return Stream.concat(
            Stream.of(Environment.CLI),
            Arrays.stream(extraEnvironments)
        ).toArray(String[]::new);
    }
}

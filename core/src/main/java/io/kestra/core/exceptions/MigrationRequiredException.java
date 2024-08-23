package io.kestra.core.exceptions;

import lombok.Getter;

import java.io.IOException;
import java.io.Serial;

@Getter
public class MigrationRequiredException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public MigrationRequiredException(String kind) {
        super("It looks like the " + kind + " migration hasn't been run, please check out the changelog for instructions to do so.");
    }
}

package io.kestra.core.exceptions;

import java.io.Serial;

import lombok.Getter;

@Getter
public class MigrationRequiredException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public MigrationRequiredException(String kind, String migrationCommand) {
        super("It looks like the " + kind + " migration hasn't been run, please run the `/app/kestra " + migrationCommand + "` command before.");
    }
}

package io.kestra.core.migration;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Triggers automatic database migration on {@code ApplicationContext.start()}.
 *
 * <p>
 * This bean is {@code @Context} and ordered {@link Ordered#HIGHEST_PRECEDENCE}, so it is eagerly
 * initialized before any repository or service bean queries the database. It delegates to
 * {@link MigrationRunnerInterface#autoRun()} — the OSS runner applies all pending scripts, while the
 * EE runner respects {@code kestra.migration.auto} and handles fresh-instance detection.
 *
 * <p>
 * The startup trigger is intentionally kept separate from {@link MigrationRunner} so the runner
 * itself is an ordinary {@code @Singleton}. The {@code kestra migrate} CLI commands resolve the
 * runner in a minimal context that never registers this trigger, and invoke the runner directly —
 * which is why no "skip auto-run" flag is needed.
 */
@Slf4j
@Context
@Order(Ordered.HIGHEST_PRECEDENCE)
@Requires(property = "kestra.repository.type")
public class MigrationStartupRunner {

    private final MigrationRunnerInterface migrationRunner;

    @Inject
    public MigrationStartupRunner(final MigrationRunnerInterface migrationRunner) {
        this.migrationRunner = migrationRunner;
    }

    @PostConstruct
    @SneakyThrows
    void onStartup() {
        migrationRunner.autoRun();
    }
}

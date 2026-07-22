package io.kestra.jdbc.migration;

/**
 * Abstract base for the 2.0.18 executions loop-run-task-id migration.
 *
 * <p>
 * Adds a generated {@code loop_run_task_id} column on the {@code executions} table (derived from
 * {@code loopRun.taskId}), so LOOP-kind iteration executions can be filtered per originating Loop task.
 */
public abstract class AbstractV2_0_18ExecutionsLoopRunTaskIdMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.18-executions-loop-run-task-id";
    }

    @Override
    public String description() {
        return "Executions: add generated loop_run_task_id column";
    }
}

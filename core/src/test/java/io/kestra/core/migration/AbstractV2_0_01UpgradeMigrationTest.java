package io.kestra.core.migration;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AbstractV2_0_01UpgradeMigration}.
 */
class AbstractV2_0_01UpgradeMigrationTest {

    @Test
    void shouldRunSchemaUpgrade() throws Exception {
        List<String> callOrder = new ArrayList<>();
        ConcreteUpgradeMigration migration = new ConcreteUpgradeMigration(
            () -> callOrder.add("schema")
        );

        migration.migrate();

        assertThat(callOrder).containsExactly("schema");
    }

    @Test
    void shouldPropagateSchemaUpgradeFailure() {
        ConcreteUpgradeMigration migration = new ConcreteUpgradeMigration(
            () ->
            {
                throw new RuntimeException("schema failed");
            }
        );

        assertThatThrownBy(migration::migrate).hasMessage("schema failed");
    }

    // --- Helpers ---

    private static class ConcreteUpgradeMigration extends AbstractV2_0_01UpgradeMigration {

        private final ThrowingRunnable schemaUpgrade;

        ConcreteUpgradeMigration(ThrowingRunnable schemaUpgrade) {
            this.schemaUpgrade = schemaUpgrade;
        }

        @Override
        protected void doSchemaUpgrade() throws Exception {
            schemaUpgrade.run();
        }

        @Override
        public String description() {
            return "test migration";
        }

        @Override
        public String checksum() {
            return "test-checksum";
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}

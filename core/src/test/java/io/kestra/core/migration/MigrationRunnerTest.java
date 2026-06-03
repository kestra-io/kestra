package io.kestra.core.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MigrationRunner} using in-memory stubs.
 */
class MigrationRunnerTest {

    private MigrationLock noOpLock;

    @BeforeEach
    void setUp() {
        noOpLock = new MigrationLock() {
            @Override
            public void acquire() {
            }

            @Override
            public void release() {
            }

            @Override
            public boolean tryAcquire() {
                return true;
            }

            @Override
            public void forceRelease() {
            }
        };
    }

    @Test
    void runAlways_appliesScriptsInLexicographicOrder() throws Exception {
        // Given: scripts provided out of order
        List<String> executionOrder = new ArrayList<>();
        List<MigrationScript> scripts = List.of(
            simpleScript("2.0.01-upgrade", () -> executionOrder.add("2.0.01-upgrade")),
            simpleScript("0-init", () -> executionOrder.add("0-init")),
            simpleScript("0-init-ee", () -> executionOrder.add("0-init-ee"))
        );
        MigrationRunner runner = new MigrationRunner(noOpLock, new InMemoryHistoryStore(), scripts);

        // When
        runner.runAlways();

        // Then: lexicographic order: "0-init" < "0-init-ee" < "2.0.01-upgrade"
        assertThat(executionOrder).containsExactly("0-init", "0-init-ee", "2.0.01-upgrade");
    }

    @Test
    void runAlways_skipsAlreadyAppliedScripts() throws Exception {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        MigrationScript script = simpleScript("2.0.01-upgrade", callCount::incrementAndGet);

        MigrationRunner runner = new MigrationRunner(noOpLock, new InMemoryHistoryStore(), List.of(script));

        // When: run twice
        runner.runAlways();
        runner.runAlways();

        // Then: script ran exactly once
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void runAlways_skipsInitScriptOnLegacyUpgrade() throws Exception {
        // Given: simulate Flyway upgrade — history store reports detectLegacyUpgrade()=true
        InMemoryHistoryStore historyStore = new InMemoryHistoryStore();
        historyStore.simulateLegacyUpgrade = true;

        AtomicInteger initCalls = new AtomicInteger(0);
        AtomicInteger upgradeCalls = new AtomicInteger(0);
        List<MigrationScript> scripts = List.of(
            simpleScript("0-init", initCalls::incrementAndGet),
            simpleScript("2.0.01-upgrade", upgradeCalls::incrementAndGet)
        );
        MigrationRunner runner = new MigrationRunner(noOpLock, historyStore, scripts);

        // When
        runner.runAlways();

        // Then: "0-init" is in INIT_SCRIPT_IDS, skipped without execution on Flyway upgrade
        assertThat(initCalls.get()).isEqualTo(0);
        // "2.0.01-upgrade" is a versioned upgrade script, should run
        assertThat(upgradeCalls.get()).isEqualTo(1);
        // Init script must be recorded in history (executionMs=0) so second startup doesn't see it as pending
        assertThat(historyStore.isApplied("0-init")).isTrue();
    }

    @Test
    void runAlways_throwsOnChecksumMismatch() throws Exception {
        // Given: a script runs once with checksum "original"
        InMemoryHistoryStore historyStore = new InMemoryHistoryStore();
        MigrationRunner runner1 = new MigrationRunner(
            noOpLock, historyStore, List.of(
                scriptWithChecksum("2.0.01-upgrade", "original-checksum")
            )
        );
        runner1.runAlways();

        // Now the same scriptId has a different checksum
        MigrationRunner runner2 = new MigrationRunner(
            noOpLock, historyStore, List.of(
                scriptWithChecksum("2.0.01-upgrade", "tampered-checksum")
            )
        );

        // When / Then
        assertThatThrownBy(runner2::runAlways)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Checksum mismatch");
    }

    @Test
    void runAlways_releasesLockEvenOnFailure() throws Exception {
        // Given
        AtomicInteger releaseCount = new AtomicInteger(0);
        MigrationLock trackingLock = new MigrationLock() {
            @Override
            public void acquire() {
            }

            @Override
            public void release() {
                releaseCount.incrementAndGet();
            }

            @Override
            public boolean tryAcquire() {
                return true;
            }

            @Override
            public void forceRelease() {
            }
        };

        MigrationScript failingScript = simpleScript("2.0.01-upgrade", () ->
        {
            throw new RuntimeException("migration failed");
        });
        MigrationRunner runner = new MigrationRunner(trackingLock, new InMemoryHistoryStore(), List.of(failingScript));

        // When / Then
        assertThatThrownBy(runner::runAlways).hasMessage("migration failed");
        assertThat(releaseCount.get()).isEqualTo(1);
    }

    @Test
    void pendingScripts_returnsOnlyUnappliedScripts() throws Exception {
        // Given: run "2.0.01-upgrade" and "2.1" first
        InMemoryHistoryStore historyStore = new InMemoryHistoryStore();
        MigrationRunner runner = new MigrationRunner(
            noOpLock, historyStore, List.of(
                simpleScript("2.0.01-upgrade", () ->
                {
                }),
                simpleScript("2.1", () ->
                {
                })
            )
        );
        runner.runAlways(); // applies both

        // Add a new script not yet applied
        MigrationScript newScript = simpleScript("2.2", () ->
        {
        });
        MigrationRunner runner2 = new MigrationRunner(
            noOpLock, historyStore, List.of(
                simpleScript("2.0.01-upgrade", () ->
                {
                }),
                simpleScript("2.1", () ->
                {
                }),
                newScript
            )
        );

        // When
        List<MigrationScript> pending = runner2.pendingScripts();

        // Then
        assertThat(pending).hasSize(1).extracting(MigrationScript::scriptId).containsExactly("2.2");
    }

    @Test
    void runAlways_acquiresAndReleasesLock() throws Exception {
        // Given
        List<String> lockEvents = new ArrayList<>();
        MigrationLock trackingLock = new MigrationLock() {
            @Override
            public void acquire() {
                lockEvents.add("acquire");
            }

            @Override
            public void release() {
                lockEvents.add("release");
            }

            @Override
            public boolean tryAcquire() {
                lockEvents.add("tryAcquire");
                return true;
            }

            @Override
            public void forceRelease() {
            }
        };
        MigrationRunner runner = new MigrationRunner(
            trackingLock, new InMemoryHistoryStore(), List.of(
                simpleScript("2.0.01-upgrade", () ->
                {
                })
            )
        );

        // When
        runner.runAlways();

        // Then: lock acquired then released
        assertThat(lockEvents).containsExactly("acquire", "release");
    }

    @Test
    void initOnStartup_skipsWhenSkipAutoRunIsTrue() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        MigrationRunner runner = new MigrationRunner(
            noOpLock, new InMemoryHistoryStore(),
            List.of(simpleScript("2.0.01-upgrade", callCount::incrementAndGet))
        );

        try {
            MigrationRunner.setSkipAutoRun(true);

            // When
            runner.initOnStartup();

            // Then
            assertThat(callCount.get()).isEqualTo(0);
        } finally {
            MigrationRunner.setSkipAutoRun(false);
        }
    }

    @Test
    void runOrFailIfLocked_succeedsWhenLockFree() throws Exception {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        MigrationRunner runner = new MigrationRunner(
            noOpLock, new InMemoryHistoryStore(),
            List.of(simpleScript("2.0.01-upgrade", callCount::incrementAndGet))
        );

        // When
        runner.runOrFailIfLocked();

        // Then
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void runOrFailIfLocked_throwsWhenLocked() {
        // Given
        MigrationLock lockedLock = new MigrationLock() {
            @Override
            public void acquire() {
            }

            @Override
            public void release() {
            }

            @Override
            public boolean tryAcquire() {
                return false;
            }

            @Override
            public void forceRelease() {
            }
        };
        MigrationRunner runner = new MigrationRunner(
            lockedLock, new InMemoryHistoryStore(),
            List.of(simpleScript("2.0.01-upgrade", () ->
            {
            }))
        );

        // When / Then
        assertThatThrownBy(runner::runOrFailIfLocked)
            .isInstanceOf(MigrationLockedException.class);
    }

    @Test
    void runOrFailIfLocked_noopWhenHasRunIsTrue() throws Exception {
        // Given
        AtomicInteger acquireCount = new AtomicInteger(0);
        MigrationLock countingLock = new MigrationLock() {
            @Override
            public void acquire() {
                acquireCount.incrementAndGet();
            }

            @Override
            public void release() {
            }

            @Override
            public boolean tryAcquire() {
                acquireCount.incrementAndGet();
                return true;
            }

            @Override
            public void forceRelease() {
            }
        };
        MigrationRunner runner = new MigrationRunner(
            countingLock, new InMemoryHistoryStore(),
            List.of(simpleScript("2.0.01-upgrade", () ->
            {
            }))
        );
        runner.runAlways();
        int acquiresAfterRunAlways = acquireCount.get();

        // When
        runner.runOrFailIfLocked();

        // Then
        assertThat(acquireCount.get()).isEqualTo(acquiresAfterRunAlways);
    }

    @Test
    void runOrFailIfLocked_releasesLockEvenOnFailure() throws Exception {
        // Given
        AtomicInteger releaseCount = new AtomicInteger(0);
        MigrationLock trackingLock = new MigrationLock() {
            @Override
            public void acquire() {
            }

            @Override
            public void release() {
                releaseCount.incrementAndGet();
            }

            @Override
            public boolean tryAcquire() {
                return true;
            }

            @Override
            public void forceRelease() {
            }
        };
        MigrationScript failingScript = simpleScript("2.0.01-upgrade", () ->
        {
            throw new RuntimeException("migration failed");
        });
        MigrationRunner runner = new MigrationRunner(trackingLock, new InMemoryHistoryStore(), List.of(failingScript));

        // When / Then
        assertThatThrownBy(runner::runOrFailIfLocked).hasMessage("migration failed");
        assertThat(releaseCount.get()).isEqualTo(1);
    }

    @Test
    void runOrFailIfLocked_noopWhenNoScripts() throws Exception {
        // Given
        AtomicInteger acquireCount = new AtomicInteger(0);
        MigrationLock countingLock = new MigrationLock() {
            @Override
            public void acquire() {
                acquireCount.incrementAndGet();
            }

            @Override
            public void release() {
            }

            @Override
            public boolean tryAcquire() {
                acquireCount.incrementAndGet();
                return true;
            }

            @Override
            public void forceRelease() {
            }
        };
        MigrationRunner runner = new MigrationRunner(countingLock, new InMemoryHistoryStore(), List.of());

        // When
        runner.runOrFailIfLocked();

        // Then
        assertThat(acquireCount.get()).isEqualTo(0);
    }

    @Test
    void forceRelease_canBeCalledWithoutPriorAcquire() throws Exception {
        // Given: a lock that tracks forceRelease calls — simulates the unlock CLI scenario
        // where forceRelease() is called on a fresh process that never acquired the lock
        AtomicInteger forceReleaseCount = new AtomicInteger(0);
        MigrationLock trackingLock = new MigrationLock() {
            @Override
            public void acquire() {
            }

            @Override
            public void release() {
            }

            @Override
            public boolean tryAcquire() {
                return true;
            }

            @Override
            public void forceRelease() {
                forceReleaseCount.incrementAndGet();
            }
        };

        // When: forceRelease is called directly (no prior acquire)
        trackingLock.forceRelease();

        // Then
        assertThat(forceReleaseCount.get()).isEqualTo(1);
    }

    @Test
    void forceRelease_releasesAcquiredLock() throws Exception {
        // Given: a lock that was previously acquired
        boolean[] locked = { false };
        MigrationLock statefulLock = new MigrationLock() {
            @Override
            public void acquire() {
                locked[0] = true;
            }

            @Override
            public void release() {
                locked[0] = false;
            }

            @Override
            public boolean tryAcquire() {
                locked[0] = true;
                return true;
            }

            @Override
            public void forceRelease() {
                locked[0] = false;
            }
        };
        statefulLock.acquire();
        assertThat(locked[0]).isTrue();

        // When
        statefulLock.forceRelease();

        // Then
        assertThat(locked[0]).isFalse();
    }

    // --- Helpers ---

    private MigrationScript simpleScript(final String scriptId, final Runnable migrationLogic) {
        return new MigrationScript() {
            @Override
            public String scriptId() {
                return scriptId;
            }

            @Override
            public String description() {
                return "Migration " + scriptId;
            }

            @Override
            public String checksum() {
                return "checksum-" + scriptId;
            }

            @Override
            public void migrate() {
                migrationLogic.run();
            }
        };
    }

    private MigrationScript scriptWithChecksum(final String scriptId, final String checksum) {
        return new MigrationScript() {
            @Override
            public String scriptId() {
                return scriptId;
            }

            @Override
            public String description() {
                return "Migration " + scriptId;
            }

            @Override
            public String checksum() {
                return checksum;
            }

            @Override
            public void migrate() {
            }
        };
    }

    /**
     * In-memory {@link MigrationHistoryStore} for unit testing {@link MigrationRunner}
     * without any external dependencies.
     */
    private static class InMemoryHistoryStore implements MigrationHistoryStore {

        /** Flip to {@code true} to simulate an upgrade from a pre-migration-system deployment. */
        boolean simulateLegacyUpgrade = false;

        private final Map<String, String> applied = new HashMap<>();

        @Override
        public void bootstrapIfNeeded() {
        }

        @Override
        public boolean hasAnyApplied() {
            return !applied.isEmpty();
        }

        @Override
        public boolean isApplied(final String scriptId) {
            return applied.containsKey(scriptId);
        }

        @Override
        public void validateChecksum(final MigrationScript script) {
            if (script.checksum() == null) {
                return;
            }
            String stored = applied.get(script.scriptId());
            if (stored != null && !stored.equals(script.checksum())) {
                throw new IllegalStateException(
                    "Checksum mismatch for script [" + script.scriptId() + "]: stored=" + stored + ", current=" + script.checksum()
                );
            }
        }

        @Override
        public void markApplied(final MigrationScript script, final long executionMs) {
            applied.put(script.scriptId(), script.checksum());
        }

        @Override
        public boolean detectLegacyUpgrade() {
            return simulateLegacyUpgrade;
        }
    }
}

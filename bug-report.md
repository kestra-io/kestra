# Bug Report — Executor Test Refactor (PR #17257 + PR #17264)

Every bug found and fixed while building the executor unit-test kit (`io.kestra.executor.testkit`),
migrating the handler tests off Micronaut, and extracting the state-store behavioral contracts.
Bugs are grouped by where they lived: production code, the test kit's own fakes, and the legacy
test suites (tests that passed without proving what they claimed).

---

## 1. Production bugs

### 1.1 Delayed executions were emitted inside the state-store transaction (the #17246 bug class)

- **Where:** `DefaultExecutor#executionDelayLoop` (executor module)
- **Fixed in:** PR #17264, commit `fix(executions): move execution-delay processing behind a transactional-outbox seam`
- **Symptom:** on the Kafka runner, `CREATE_NEW_EXECUTION` retries were silently dropped —
  `KafkaRunnerRetryTest.retryNewExecution{Task,Flow}{Attempts,Duration}` all timed out after 30s.
- **Root cause:** the loop called `toExecution()` — which publishes the execution's event to the
  queue — from **inside** `processExpired()`'s JDBC transaction. For a `RESTART_FAILED_FLOW`
  delay, that event references a brand-new execution INSERTed by `replay()` within the same
  still-open transaction. On a broker that is not transactional with the repository (Kafka), the
  executor's own consumer can receive the event before the INSERT is visible: its
  `executionStateStore.lock()` finds no row, hits the "not ready for now, skip and wait for a
  first state" branch, returns empty — and the offset is committed anyway. The retry chain never
  runs. JDBC backends are immune because their queue is a table in the same database.
- **Fix:** rather than only moving the emission (which any future edit could regress), the
  per-delay logic moved verbatim into a new `ExecutionDelayProcessor` that has **no queue
  dependency at all** — it persists state and returns the `ExecutorContext`s;
  `DefaultExecutor`'s loop emits them only after `processExpired()` returns, i.e. after the
  transaction commits. The transactional-outbox rule is now enforced by construction.
- **Regression net:** `ExecutionDelayProcessorTest` (plain JUnit, ~ms) asserts the outbox
  invariant — processing persists the replayed execution under its own id and emits **nothing**
  on any of the harness's queue channels — plus the full per-delay-type decision matrix. The
  end-to-end twin (`KafkaRunnerRetryTest`) needs 30s+ and a real broker.
- **Note:** `develop` independently merged the minimal inline fix for #17246 (collect-then-emit)
  while this refactor was in flight; the extraction here supersedes it structurally.

### 1.2 Queued-execution pop emitted inside the `decrementAndPop` transaction (same class)

- **Where:** `DefaultExecutor#toExecution` (executor module)
- **Fixed in:** PR #17264, commit `fix(executions): move concurrency-slot release behind a transactional-outbox seam`
- **Root cause:** when a terminated execution freed its concurrency slot, the popped queued
  execution was emitted (`executionQueue.emit`) and its flow triggers fired **inside** the
  `ConcurrencyLimitStateStore#decrementAndPop` transaction — the same
  observable-before-commit shape as 1.1.
- **Fix:** the release guards and the decrement/pop moved verbatim into
  `ConcurrencySlotReleaseProcessor` (no queue dependency); it returns the popped execution and
  `DefaultExecutor` emits it only after the transaction commits.
- **Regression net:** `ConcurrencySlotReleaseProcessorTest` (7 tests, ~ms) — the outbox
  invariant plus the three release guards (queued-then-killed, concurrency short-circuit
  termination, duplicate KILLED events) that were previously buried in `DefaultExecutor` and
  untestable without a full runner.

### 1.3 SLA-monitor loop emitted inside the SLA store's transaction (same class)

- **Where:** `DefaultExecutor#executionSLAMonitorLoop` (executor module)
- **Fixed in:** PR #17264, commit `fix(executions): move SLA-monitor processing behind a transactional-outbox seam`
- **Root cause:** the loop called `toExecution()` — which emits the execution's
  TERMINATED/UPDATED events — from **inside** `SLAMonitorStateStore#processExpired`'s
  transaction.
- **Fix:** the per-monitor logic moved verbatim into `SLAMonitorProcessor` (mirrors
  `ExecutionDelayProcessor`); the loop emits the returned contexts after the transaction
  commits.
- **Known residual (documented, pinned by a test, not fixed):** for FAIL/CANCEL SLA behaviors,
  `ExecutorService#processViolation` → `markAs` emits the `ExecutionKilledExecution` kill
  request from **inside** the execution lock. That emission is shared with the
  execution-changed SLA path (`handleExecutionChangedSLA`, called by the event handler), so it
  needs its own seam — an emission accumulator on `ExecutorContext` — rather than a local fix.

### 1.4 Removing the last SLA from a flow wedged the SLA-monitor loop (NPE)

- **Where:** the SLA-monitor loop (previously `DefaultExecutor#executionSLAMonitorLoop`, now
  `SLAMonitorProcessor`)
- **Found by:** writing `SLAMonitorProcessorTest.shouldIgnoreMonitorWhenSlaWasRemovedFromFlow`
  — the migrated code NPE'd on the first run of the test
- **Root cause:** the "SLA removed from the flow" guard called `flow.getSla().stream()`
  directly. It only covered removing *one* SLA while others remain; updating the flow to remove
  the **last** SLA leaves `getSla()` null → NPE inside `processExpired()`'s transaction. On
  JDBC the transaction rolls back, the monitor is never consumed, and the loop re-throws on
  every tick — SLA processing wedges for **all** monitors until the flow is changed back.
- **Fix:** `ListUtils.emptyOnNull(flow.getSla())`, the same null-safety `SLAService` already
  uses; the stale monitor is now consumed and skipped.

---

## 2. Test-kit fake-fidelity bugs (caught by the state-store contracts)

These are bugs in the in-memory fakes the unit-test kit runs on. They matter because a diverging
fake silently invalidates every test built on it — which is exactly why the refactor extracted
annotation-free behavioral contracts that run identically against the JDBC/Elasticsearch stores
and the fakes.

### 2.1 `InMemoryExecutionStateStore.lock` persisted under the wrong id on replay

- **Fixed in:** PR #17264 (same commit as 1.1)
- **Divergence:** the fake persisted the callback's returned execution under the **locked** id.
  When the callback returns an execution with a *different* id — the replay /
  `CREATE_NEW_EXECUTION` retry case — the fake overwrote the original row and lost the new one
  under its own id. The production JDBC implementation
  (`AbstractJdbcExecutionRepository#lock`) INSERTs the returned execution under **its own** id
  and leaves the locked row untouched.
- **Impact if unfixed:** any kit test around replay semantics would have asserted against
  behavior production does not have.
- **Fix + net:** the fake now persists under the returned execution's id; the contract gained
  `shouldInsertReturnedExecutionAndKeepOriginalWhenLockReturnsDifferentId`, which now runs
  against H2/Postgres/MySQL/EE-Elasticsearch **and** the fake.

### 2.2 `InMemoryConcurrencyLimitStateStore.decrementAndPop` did not hand the freed slot to the popped execution

- **Fixed in:** PR #17264, commit `test(executions): add concurrency-limit lifecycle sagas and state-store contract`
- **Divergence:** on pop, `AbstractJdbcConcurrencyLimitStateStore` re-increments the running
  counter inside the pop consumer — the dequeued execution takes over the freed slot. The fake
  popped without incrementing, so after a pop its counter read one lower than production's.
- **Impact if unfixed:** kit tests asserting counter values across a pop would have encoded the
  wrong arithmetic; under a limit of 1, the fake would have allowed a second pop that production
  forbids.
- **Fix + net:** the fake now increments inside the pop consumer, mirroring the JDBC
  implementation; `ConcurrencyLimitStateStoreContract` (FIFO pop with slot handover,
  queued-protection guard, decrement floor) runs against H2 and the fake.

---

## 3. Lying legacy tests (fixed during the Micronaut → testkit migration)

Found in PR #17257 while porting the 7 handler test suites to plain JUnit. These tests were
green while proving nothing — worse than missing coverage, because they looked like coverage.

### 3.1 `SubflowExecutionResultMessageHandlerTest` — happy path tested the failure branch

The "happy path" test seeded an execution **without task runs**, so the handler took the
not-joinable/failure branch instead of the join it claimed to verify. The migrated test now
exercises the real join branch, including that the subflow's saved outputs are merged into the
parent's task run.

### 3.2 `SubflowExecutionEndMessageHandlerTest` — assertion-free while the handler swallowed exceptions

The smoke test asserted nothing, and the handler swallows exceptions internally — so it passed
even if the handler failed completely. It now asserts the actual `SubflowExecutionResult`
emission.

### 3.3 `MultipleConditionEventMessageHandlerTest` — assertion-free smoke

Same pattern: no assertions. It now asserts the `ExecutionCommand` emission (including
terminal-state preservation) and the no-emission case.

---

## 4. Production traps discovered and documented (not bugs fixed, but behavior pinned by tests)

Surprising behaviors found while writing the decision matrices. Each is now documented by a
test so a future change that alters it fails loudly:

- `Execution.builder().build()` → `prebuild()` silently overwrites builder-supplied
  `metadata`/`originalId` (use `.withMetadata()` after build).
- Pause `behavior` is ignored when only `timeout` is set — the delay state comes from
  `State.Type.fail(task)`.
- Flow-level `CREATE_NEW_EXECUTION` retry dates are computed from wall-clock
  (`execution.getState().maxDate()`), not from injected time.
- The `handleAfterExecution ` from-label carries a trailing space.
- The executor emits flowable parents as worker tasks that production never sends to a worker
  (`Task#isSendToWorkerTask`); the event handler flips them to RUNNING.
- `Parallel` re-emits still-CREATED siblings on every cycle (the SUBMITTED transition lives in
  the message handler, not `ExecutorService`).
- `ExecutableUtils#guessState` treats a CANCELLED child as SUCCESS even with
  `transmitFailed=true`; PAUSED propagates verbatim.
- A missing or disabled subflow child fails the task run in-cycle, but the parent execution only
  fails on the **next** event cycle.

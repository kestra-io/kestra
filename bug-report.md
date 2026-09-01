# Bug Report — Executor Test Refactor (PR #17257)

Bugs found and fixed while building the executor test harness, in three categories: actual bugs
(production code and legacy tests), bugs in the harness's own fakes, and surprising production
behaviors that were pinned by tests rather than changed.

## Actual bug fixes

1. **Delayed executions were emitted inside the state-store transaction** — the
   `DefaultExecutor` delay loop published queue events before the transaction committed; on
   Kafka, `CREATE_NEW_EXECUTION` retries were silently dropped (the #17246 bug class). Fixed by
   extracting `ExecutionDelayProcessor` (no queue dependency); the loop emits after commit.
2. **Queued-execution pop was emitted inside the `decrementAndPop` transaction** — same class.
   Fixed by extracting `ConcurrencySlotReleaseProcessor`.
3. **SLA-monitor loop emitted inside the SLA store's transaction** — same class. Fixed by
   extracting `SLAMonitorProcessor`.
4. **Removing the last SLA of a flow wedged the SLA-monitor loop** — `flow.getSla().stream()`
   NPE'd, the transaction rolled back, the monitor was never consumed, and the loop re-threw on
   every tick for **all** monitors. Fixed with `ListUtils.emptyOnNull`.
5. **Three lying legacy tests** — green while proving nothing, fixed during the
   Micronaut → testkit migration:
   - `SubflowExecutionResultMessageHandlerTest` — the "happy path" actually exercised the
     failure branch; it now tests the real join, including output merging.
   - `SubflowExecutionEndMessageHandlerTest` — assertion-free while the handler swallows
     exceptions; it now asserts the `SubflowExecutionResult` emission.
   - `MultipleConditionEventMessageHandlerTest` — assertion-free; it now asserts the
     `ExecutionCommand` emission and the no-emission case.

## Harness bug fixes

Divergences between the in-memory fakes and production, caught by the state-store contracts —
each fix is pinned by a contract scenario that runs against the JDBC stores **and** the fakes.

1. **`InMemoryExecutionStateStore.lock` persisted under the wrong id on replay** — production
   INSERTs the returned execution under its own id and keeps the locked row; the fake overwrote
   the locked row instead.
2. **`InMemoryConcurrencyLimitStateStore.decrementAndPop` did not hand the freed slot to the
   popped execution** — production re-increments the counter inside the pop consumer; the
   fake's counter read one lower after every pop.

## Surprising behaviors (pinned by tests, not changed)

Each of these is documented by a test so a change that alters it fails loudly:

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

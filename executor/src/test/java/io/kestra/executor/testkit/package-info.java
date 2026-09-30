/**
 * Unit-test kit for the executor: run the <b>real</b> {@link io.kestra.executor.ExecutorService}
 * and all message handlers as plain code — no Micronaut context, no database, no queues, no
 * threads — and assert on what the executor <i>decided</i>, not on what a backend stored.
 *
 * <h2>Shape</h2>
 * <ul>
 * <li>{@link io.kestra.executor.testkit.ExecutorTestHarness} — the composition root. Everything
 * with executor logic is the production class; everything else is an in-memory fake
 * ({@code InMemory*StateStore}), a recording queue ({@code Recording*Queue} — synchronous
 * assertion channels), or a Mockito mock exposed for per-test stubbing.</li>
 * <li>{@link io.kestra.executor.testkit.Flows}, {@link io.kestra.executor.testkit.Executions},
 * {@link io.kestra.executor.testkit.Results} — fixture factories for the given/when steps.</li>
 * <li>{@link io.kestra.executor.testkit.ExecutorContextAssert} — AssertJ entry point for the
 * {@code ExecutorContext} command object the executor returns.</li>
 * </ul>
 * The tests built on the kit live in {@code io.kestra.executor.statemachine}: one class per
 * decision dimension (retry, errors/finally, killing, pause, flowable traversal, subflows,
 * flow triggers, concurrency, quotas), each written as given/when/then sagas.
 *
 * <h2>Why trust the fakes</h2>
 * The fakes are not free-hand reimplementations: the state-store behavior is specified by
 * annotation-free contract classes ({@code ExecutionStateStoreContract},
 * {@code ConcurrencyLimitStateStoreContract}, {@code MultipleConditionStateStoreContract})
 * whose scenarios run unchanged against the JDBC/Elasticsearch implementations <b>and</b>
 * against these fakes. A fake that drifts from production semantics fails its contract run.
 *
 * <h2>Adding a test</h2>
 * <ol>
 * <li>{@code ExecutorTestHarness harness = ExecutorTestHarness.create();}</li>
 * <li>Given: {@code harness.registerFlow(flow)} and seed state via the exposed stores.</li>
 * <li>When: {@code harness.process(execution)} to run full event cycles, or call a single
 * handler (e.g. {@code harness.executionEventMessageHandler().handle(event)}) to pin one
 * decision.</li>
 * <li>Then: assert on the returned {@code ExecutorContext} and on the recording queues —
 * what was emitted is as much the contract as what was stored.</li>
 * </ol>
 *
 * <h2>When to use what</h2>
 * <ul>
 * <li>Executor decision logic (state transitions, gates, ordering) → this kit, milliseconds.</li>
 * <li>Store semantics (locking, transactions, SQL) → the {@code *Contract} classes through the
 * backend {@code @MicronautTest} shells.</li>
 * <li>End-to-end wiring across components → the runner tests ({@code H2RunnerTest} etc.).</li>
 * </ul>
 * When a fake gains behavior, add the matching scenario to the store's contract in the same
 * change — the contract is what keeps the kit honest.
 */
package io.kestra.executor.testkit;

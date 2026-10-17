/**
 * Unit-test kit for the executor: the <b>real</b> {@link io.kestra.executor.DefaultExecutor},
 * {@link io.kestra.executor.ExecutorService} and message handlers as plain code — no Micronaut
 * context, no database, no threads — over in-memory fakes and recording queues.
 *
 * <h2>Driving the executor</h2>
 * {@link io.kestra.executor.testkit.ExecutorTestHarness} is the composition root. Two verbs drive
 * the whole machine, one queue delivery at a time, exactly as production would receive it:
 * <ul>
 * <li>{@code harness.step(message)} delivers one message and returns what the executor emitted
 * in return, as {@link io.kestra.executor.testkit.Trace.Emission}s.</li>
 * <li>{@code harness.run(messages, worker)} drains every queue while a
 * {@link io.kestra.executor.testkit.ScriptedWorker} answers each dispatched task, and returns the
 * {@link io.kestra.executor.testkit.Trace} of every delivery and its emissions.</li>
 * <li>{@code harness.tickExecutionDelays(now)} moves the {@link io.kestra.executor.testkit.MutableClock}
 * and fires the delay loop once.</li>
 * </ul>
 * Queue names in a trace are the {@code Trace.*} constants. Assert the outcome with
 * {@link io.kestra.executor.testkit.HarnessAssert} (persisted state, running counters, queued
 * executions, pending delays) and the trace; a failure should read as a sentence, so give every
 * assertion an {@code as(...)}.
 *
 * <h2>Pinning one decision</h2>
 * To look at a single executor cycle rather than the whole run, call a handler directly
 * ({@code harness.executionEventMessageHandler().handle(event)}) or {@code harness.process(flow, execution)},
 * and assert on the returned {@code ExecutorContext} with
 * {@link io.kestra.executor.testkit.ExecutorContextAssert}.
 *
 * <h2>Fixtures</h2>
 * {@link io.kestra.executor.testkit.Flows} (builders or YAML), {@link io.kestra.executor.testkit.Executions}
 * and {@link io.kestra.executor.testkit.Results} build the given/when steps. Collaborators without
 * executor logic are Mockito mocks exposed for per-test stubbing.
 *
 * <h2>Why trust the fakes</h2>
 * The state-store fakes are specified by annotation-free contract classes
 * ({@code ExecutionStateStoreContract}, {@code ConcurrencyLimitStateStoreContract},
 * {@code MultipleConditionStateStoreContract}) whose scenarios run unchanged against the
 * JDBC/Elasticsearch implementations <b>and</b> against the fakes. When a fake gains behavior, add
 * the matching scenario to the contract in the same change.
 *
 * <h2>When to use what</h2>
 * <ul>
 * <li>Executor decision logic (state transitions, gates, ordering) → this kit, milliseconds.</li>
 * <li>Store semantics (locking, transactions, SQL) → the {@code *Contract} classes through the
 * backend {@code @MicronautTest} shells.</li>
 * <li>End-to-end wiring across components → the runner tests ({@code H2RunnerTest} etc.).</li>
 * </ul>
 */
package io.kestra.executor.testkit;

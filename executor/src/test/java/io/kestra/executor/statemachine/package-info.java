/**
 * Decision-matrix tests for the executor state machine, built on
 * {@link io.kestra.executor.testkit.ExecutorTestHarness} — plain JUnit, no Micronaut, no
 * database. One class per decision dimension (retry, errors/finally, killing, pause, flowable
 * traversal, subflows, flow triggers, concurrency, quotas), written as given/when/then sagas
 * over full event cycles.
 * <p>
 * These are new tests only: the legacy {@code @MicronautTest}/runner suites remain untouched as
 * the integration twin. A behavior pinned here that looks surprising is intentional — it
 * documents what production does today, so a change that alters it fails loudly instead of
 * silently shipping.
 * <p>
 * {@code domain} holds pure-domain matrices (no harness needed), e.g. retry-date computation
 * and subflow final-state guessing.
 */
package io.kestra.executor.statemachine;

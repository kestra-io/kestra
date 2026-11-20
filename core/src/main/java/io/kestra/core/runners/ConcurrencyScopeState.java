package io.kestra.core.runners;

/**
 * The running count of a concurrency scope, read under the store's lock: the one place that
 * decides whether the scope has room for one more execution.
 */
public record ConcurrencyScopeState(ScopedConcurrencyLimit scope, int running) {
    /**
     * A scope whose limit was removed while an admitted execution ran is never full.
     */
    public boolean isFull() {
        return scope.concurrency() != null && running >= scope.concurrency().getLimit();
    }
}

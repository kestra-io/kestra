package io.kestra.worker.stores;

/**
 * Matches when the server reaches the KV store through the controller: a worker, asked to.
 */
public class KVStoreFromControllerCondition extends WorkerAccessFromControllerCondition {

    public static final String CONFIG_KEY = "kestra.kv.workerAccess";

    @Override
    protected String configKey() {
        return CONFIG_KEY;
    }
}

package io.kestra.worker.stores;

/**
 * Matches when the server reaches the content of the namespace files through the controller: a worker, asked to.
 */
public class NamespaceFilesFromControllerCondition extends WorkerAccessFromControllerCondition {

    public static final String CONFIG_KEY = "kestra.namespaceFiles.workerAccess";

    @Override
    protected String configKey() {
        return CONFIG_KEY;
    }
}

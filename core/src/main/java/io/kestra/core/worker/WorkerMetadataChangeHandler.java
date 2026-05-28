package io.kestra.core.worker;

public interface WorkerMetadataChangeHandler {

    void onMetadataChange(MetadataChangePayload payload);

    /**
     * Invoked after each gRPC stream (re-)connect so the handler can flush any local state that
     * may have diverged from the controller while the stream was down.
     */
    void onReconnect();
}

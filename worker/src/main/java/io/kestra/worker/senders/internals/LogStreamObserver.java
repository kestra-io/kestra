package io.kestra.worker.senders.internals;

import com.google.protobuf.GeneratedMessageV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO
public class LogStreamObserver<T extends GeneratedMessageV3> implements io.grpc.stub.StreamObserver<T> {

    private static final Logger LOG = LoggerFactory.getLogger(LogStreamObserver.class);

    @Override
    public void onNext(T value) {
        // noop
    }

    @Override
    public void onError(Throwable t) {
        LOG.error("Error while sending request", t);
    }

    @Override
    public void onCompleted() {
        // noop
    }
}

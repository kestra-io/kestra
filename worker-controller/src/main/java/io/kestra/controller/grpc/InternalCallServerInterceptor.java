package io.kestra.controller.grpc;

import io.kestra.core.security.InternalCallContext;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Marks every call reaching the controller as an internal call, so that a worker is not subjected
 * to the row-level permission filters that only a user request can satisfy.
 * <p>
 * The marker is bound around the three places a service method body runs: the handler start for a
 * client-streaming method, {@code onMessage} for a streaming one and {@code onHalfClose} for a
 * unary one.
 *
 * @see InternalCallContext
 */
public class InternalCallServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        final ServerCall<ReqT, RespT> call,
        final Metadata headers,
        final ServerCallHandler<ReqT, RespT> next) {

        ServerCall.Listener<ReqT> delegate = InternalCallContext.callAsInternalCall(() -> next.startCall(call, headers));

        return new SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                InternalCallContext.runAsInternalCall(() -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
                InternalCallContext.runAsInternalCall(super::onHalfClose);
            }
        };
    }
}

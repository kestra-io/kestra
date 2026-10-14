package io.kestra.controller.grpc;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.kestra.core.security.InternalCallContext;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InternalCallServerInterceptorTest {

    @Test
    void shouldMarkHandlerStartAndListenerCallbacksAsInternalCall() {
        AtomicBoolean internalOnStartCall = new AtomicBoolean();
        AtomicBoolean internalOnHalfClose = new AtomicBoolean();
        AtomicBoolean internalOnMessage = new AtomicBoolean();

        ServerCallHandler<String, String> handler = (call, headers) ->
        {
            internalOnStartCall.set(InternalCallContext.isInternalCall());
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    internalOnMessage.set(InternalCallContext.isInternalCall());
                }

                @Override
                public void onHalfClose() {
                    internalOnHalfClose.set(InternalCallContext.isInternalCall());
                }
            };
        };

        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = mock(ServerCall.class);

        ServerCall.Listener<String> listener = new InternalCallServerInterceptor().interceptCall(call, new Metadata(), handler);
        listener.onMessage("request");
        listener.onHalfClose();

        assertThat(internalOnStartCall).isTrue();
        assertThat(internalOnMessage).isTrue();
        assertThat(internalOnHalfClose).isTrue();
        assertThat(InternalCallContext.isInternalCall()).isFalse();
    }
}

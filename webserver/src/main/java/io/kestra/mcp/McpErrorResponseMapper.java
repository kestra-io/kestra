package io.kestra.mcp;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import static io.modelcontextprotocol.spec.McpSchema.JSONRPC_VERSION;

@Singleton
public class McpErrorResponseMapper {
    @NonNull
    public Mono<HttpResponse<?>> exceptionResponse(Throwable t, McpSchema.JSONRPCMessage msg) {
        var error = mcpError(McpSchema.ErrorCodes.INTERNAL_ERROR, "Failed to handle request: " + t.getMessage());
        return exceptionResponse(error, msg);
    }

    @NonNull
    public Mono<HttpResponse<?>> exceptionResponse(McpError err, McpSchema.JSONRPCMessage msg) {
        var rsp = errorJsonrpcResponse(msg, err);
        return Mono.just(HttpResponse.status(status(rsp)).body(rsp));
    }

    @NonNull
    private static HttpStatus status(@Nullable McpSchema.JSONRPCResponse response) {
        return response == null || response.error() == null
            ? HttpStatus.OK
            : status(response.error());
    }

    @NonNull
    private static HttpStatus status(@NonNull McpSchema.JSONRPCResponse.JSONRPCError error) {
        return switch (error.code()) {
            case McpSchema.ErrorCodes.PARSE_ERROR,
                 McpSchema.ErrorCodes.INVALID_REQUEST,
                 McpSchema.ErrorCodes.METHOD_NOT_FOUND,
                 McpSchema.ErrorCodes.INVALID_PARAMS -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @NonNull
    private static McpError mcpError(int code, String message) {
        return McpError.builder(code).message(message).build();
    }

    @NonNull
    private static McpSchema.JSONRPCResponse errorJsonrpcResponse(@NonNull McpSchema.JSONRPCMessage message,
                                                          @NonNull McpError error) {
        var jsonrpcError = error.getJsonRpcError() != null
            ? error.getJsonRpcError()
            : new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INTERNAL_ERROR, error.getMessage(), null);

        Object id = (message instanceof McpSchema.JSONRPCRequest req) ? req.id() : null;

        return new McpSchema.JSONRPCResponse(JSONRPC_VERSION, id, null, jsonrpcError);
    }
}

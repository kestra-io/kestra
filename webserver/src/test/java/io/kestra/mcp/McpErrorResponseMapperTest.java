package io.kestra.mcp;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpErrorResponseMapperTest {

    private final McpErrorResponseMapper mapper = new McpErrorResponseMapper();

    @Test
    void shouldReturnJsonRpcErrorWithInternalErrorStatusWhenThrowableProvided() {
        // Given
        RuntimeException throwable = new RuntimeException("something went wrong");
        McpSchema.JSONRPCRequest msg = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, "test/method", "req-1", null);

        // When
        HttpResponse<?> response = mapper.exceptionResponse(throwable, msg).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
        McpSchema.JSONRPCResponse body = (McpSchema.JSONRPCResponse) response.body();
        assertThat(body).isNotNull();
        assertThat(body.jsonrpc()).isEqualTo(McpSchema.JSONRPC_VERSION);
        assertThat(body.error()).isNotNull();
        assertThat(body.error().code()).isEqualTo(McpSchema.ErrorCodes.INTERNAL_ERROR);
        assertThat(body.error().message()).isEqualTo("Failed to handle request: " + throwable.getMessage());
    }

    @Test
    void shouldReturnJsonRpcErrorWithMcpErrorMessageWhenMcpErrorProvided() {
        // Given
        String errorMessage = "custom mcp error message";
        McpError mcpError = McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR).message(errorMessage).build();
        McpSchema.JSONRPCRequest msg = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, "test/method", "req-2", null);

        // When
        HttpResponse<?> response = mapper.exceptionResponse(mcpError, msg).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
        McpSchema.JSONRPCResponse body = (McpSchema.JSONRPCResponse) response.body();
        assertThat(body).isNotNull();
        assertThat(body.jsonrpc()).isEqualTo(McpSchema.JSONRPC_VERSION);
        assertThat(body.error()).isNotNull();
        assertThat(body.error().code()).isEqualTo(McpSchema.ErrorCodes.INTERNAL_ERROR);
        assertThat(body.error().message()).isEqualTo(errorMessage);
    }
}

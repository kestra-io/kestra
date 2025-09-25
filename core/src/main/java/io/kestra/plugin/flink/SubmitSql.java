package io.kestra.plugin.flink;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Submit a SQL statement to Flink SQL Gateway.",
    description = "This task submits a SQL statement to Apache Flink via the SQL Gateway. " +
                  "No JAR file is required as the SQL is executed directly by Flink."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a streaming SQL query",
            full = true,
            code = """
                id: flink-sql-streaming
                namespace: company.team

                tasks:
                  - id: run-sql
                    type: io.kestra.plugin.flink.SubmitSql
                    gatewayUrl: "http://flink-sql-gateway:8083"
                    statement: |
                      INSERT INTO enriched_orders
                      SELECT o.order_id, o.customer_id, c.name, o.amount, o.order_time
                      FROM orders o
                      JOIN customers c ON o.customer_id = c.id
                    sessionConfig:
                      catalog: "default_catalog"
                      database: "default_database"
                      configuration:
                        execution.runtime-mode: "streaming"
                        execution.checkpointing.interval: "30s"
                """
        ),
        @Example(
            title = "Execute a batch SQL query",
            code = """
                id: run-batch-sql
                type: io.kestra.plugin.flink.SubmitSql
                gatewayUrl: "http://flink-sql-gateway:8083"
                statement: |
                  CREATE TABLE daily_summary AS
                  SELECT DATE(order_time) as order_date,
                         COUNT(*) as order_count,
                         SUM(amount) as total_amount
                  FROM orders
                  WHERE order_time >= '2024-01-01'
                  GROUP BY DATE(order_time)
                sessionConfig:
                  configuration:
                    execution.runtime-mode: "batch"
                """
        )
    }
)
public class SubmitSql extends Task implements RunnableTask<SubmitSql.Output> {

    @Schema(
        title = "SQL Gateway URL",
        description = "The base URL of the Flink SQL Gateway, e.g., 'http://flink-sql-gateway:8083'"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> gatewayUrl;

    @Schema(
        title = "SQL statement",
        description = "The SQL statement to execute. Supports both DDL and DML statements."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> statement;

    @Schema(
        title = "Session name",
        description = "Optional session name. If not provided, a random session will be created."
    )
    @PluginProperty(dynamic = true)
    private Property<String> sessionName;

    @Schema(
        title = "Session configuration",
        description = "Session configuration including catalog, database, and Flink configuration properties."
    )
    @PluginProperty(dynamic = true)
    private Property<SessionConfig> sessionConfig;

    @Schema(
        title = "Connection timeout",
        description = "Timeout for connecting to the SQL Gateway in seconds. Defaults to 30."
    )
    @PluginProperty
    @Builder.Default
    private Property<Integer> connectionTimeout = Property.of(30);

    @Schema(
        title = "Statement timeout",
        description = "Timeout for SQL statement execution in seconds. Defaults to 300."
    )
    @PluginProperty
    @Builder.Default
    private Property<Integer> statementTimeout = Property.of(300);

    @Override
    public SubmitSql.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String renderedGatewayUrl = runContext.render(this.gatewayUrl).as(String.class).orElseThrow();
        String renderedStatement = runContext.render(this.statement).as(String.class).orElseThrow();

        logger.info("Executing SQL statement via Flink SQL Gateway at: {}", renderedGatewayUrl);

        // Create or get session
        String sessionHandle = createOrGetSession(runContext, renderedGatewayUrl);

        try {
            // Execute SQL statement
            String operationHandle = executeStatement(runContext, renderedGatewayUrl, sessionHandle, renderedStatement);

            // Wait for completion and get results
            OperationResult result = waitForOperationCompletion(runContext, renderedGatewayUrl, sessionHandle, operationHandle);

            logger.info("SQL statement executed successfully. Operation handle: {}", operationHandle);

            return Output.builder()
                .operationHandle(operationHandle)
                .sessionHandle(sessionHandle)
                .resultCount(result.getRowCount())
                .status(result.getStatus())
                .build();

        } finally {
            // Close session if we created it
            if (sessionName == null) {
                closeSession(runContext, renderedGatewayUrl, sessionHandle);
            }
        }
    }

    private String createOrGetSession(RunContext runContext, String gatewayUrl)
            throws IOException, InterruptedException, IllegalVariableEvaluationException {

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(runContext.render(connectionTimeout).as(Integer.class).orElse(30)))
            .build();

        String sessionName = this.sessionName != null ?
            runContext.render(this.sessionName).as(String.class).orElse(null) : null;

        if (sessionName != null) {
            // Try to get existing session
            HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/v1/sessions/" + sessionName))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            if (getResponse.statusCode() == 200) {
                runContext.logger().info("Using existing session: {}", sessionName);
                return sessionName;
            }
        }

        // Create new session
        StringBuilder payload = new StringBuilder();
        payload.append("{");

        if (sessionName != null) {
            payload.append("\"sessionName\":\"").append(sessionName).append("\"");
        }

        if (sessionConfig != null) {
            SessionConfig config = runContext.render(sessionConfig).as(SessionConfig.class).orElse(null);
            if (config != null) {
                if (sessionName != null) payload.append(",");

                if (config.getCatalog() != null) {
                    payload.append("\"catalog\":\"").append(config.getCatalog()).append("\"");
                }

                if (config.getDatabase() != null) {
                    if (config.getCatalog() != null) payload.append(",");
                    payload.append("\"database\":\"").append(config.getDatabase()).append("\"");
                }

                if (config.getConfiguration() != null && !config.getConfiguration().isEmpty()) {
                    if (config.getCatalog() != null || config.getDatabase() != null) payload.append(",");
                    payload.append("\"properties\":{");
                    boolean first = true;
                    for (Map.Entry<String, String> entry : config.getConfiguration().entrySet()) {
                        if (!first) payload.append(",");
                        payload.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                        first = false;
                    }
                    payload.append("}");
                }
            }
        }

        payload.append("}");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gatewayUrl + "/v1/sessions"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create session: " + response.statusCode() + " - " + response.body());
        }

        // Extract session handle from response
        String sessionHandle = extractSessionHandleFromResponse(response.body());
        runContext.logger().info("Created new session: {}", sessionHandle);
        return sessionHandle;
    }

    private String executeStatement(RunContext runContext, String gatewayUrl, String sessionHandle, String statement)
            throws IOException, InterruptedException, IllegalVariableEvaluationException {

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        String payload = "{\"statement\":\"" + statement.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gatewayUrl + "/v1/sessions/" + sessionHandle + "/statements"))
            .timeout(Duration.ofSeconds(runContext.render(statementTimeout).as(Integer.class).orElse(300)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to execute statement: " + response.statusCode() + " - " + response.body());
        }

        return extractOperationHandleFromResponse(response.body());
    }

    private OperationResult waitForOperationCompletion(RunContext runContext, String gatewayUrl,
                                                       String sessionHandle, String operationHandle)
            throws IOException, InterruptedException, IllegalVariableEvaluationException {

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        int timeout = runContext.render(statementTimeout).as(Integer.class).orElse(300);
        long startTime = System.currentTimeMillis();

        while (true) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/v1/sessions/" + sessionHandle + "/operations/" + operationHandle + "/status"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to get operation status: " + response.statusCode() + " - " + response.body());
            }

            String status = extractStatusFromResponse(response.body());

            if ("FINISHED".equals(status)) {
                return new OperationResult(status, extractRowCountFromResponse(response.body()));
            } else if ("ERROR".equals(status) || "CANCELED".equals(status)) {
                throw new RuntimeException("Operation failed with status: " + status + " - " + response.body());
            }

            // Check timeout
            if (System.currentTimeMillis() - startTime > timeout * 1000L) {
                throw new RuntimeException("Operation timed out after " + timeout + " seconds");
            }

            // Wait before next check
            Thread.sleep(1000);
        }
    }

    private void closeSession(RunContext runContext, String gatewayUrl, String sessionHandle) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/v1/sessions/" + sessionHandle))
                .timeout(Duration.ofSeconds(30))
                .DELETE()
                .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());
            runContext.logger().info("Closed session: {}", sessionHandle);
        } catch (Exception e) {
            runContext.logger().warn("Failed to close session: {}", sessionHandle, e);
        }
    }

    // Simple JSON extraction methods
    private String extractSessionHandleFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"sessionHandle\":\\s*\"([^\"]+)\"");
        if (parts.length > 1) {
            return parts[1].split("\"")[0];
        }
        throw new RuntimeException("Could not extract session handle from response: " + responseBody);
    }

    private String extractOperationHandleFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"operationHandle\":\\s*\"([^\"]+)\"");
        if (parts.length > 1) {
            return parts[1].split("\"")[0];
        }
        throw new RuntimeException("Could not extract operation handle from response: " + responseBody);
    }

    private String extractStatusFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"status\":\\s*\"([^\"]+)\"");
        if (parts.length > 1) {
            return parts[1].split("\"")[0];
        }
        return "UNKNOWN";
    }

    private int extractRowCountFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"rowCount\":\\s*(\\d+)");
        if (parts.length > 1) {
            return Integer.parseInt(parts[1].split(",")[0].trim());
        }
        return -1;
    }

    @Builder
    @Getter
    public static class SessionConfig {
        private final String catalog;
        private final String database;
        private final Map<String, String> configuration;
    }

    @Builder
    @Getter
    public static class OperationResult {
        private final String status;
        private final int rowCount;

        public OperationResult(String status, int rowCount) {
            this.status = status;
            this.rowCount = rowCount;
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The operation handle",
            description = "The unique identifier for the executed SQL operation"
        )
        private final String operationHandle;

        @Schema(
            title = "The session handle",
            description = "The unique identifier for the SQL Gateway session"
        )
        private final String sessionHandle;

        @Schema(
            title = "Result count",
            description = "Number of rows affected or returned by the operation"
        )
        private final Integer resultCount;

        @Schema(
            title = "Operation status",
            description = "Final status of the operation"
        )
        private final String status;
    }
}
package io.kestra.plugin.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Submit a SQL statement to Flink SQL Gateway.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Submit SQL to Flink SQL Gateway",
    description = "Execute SQL statements via Flink SQL Gateway without requiring a JAR file."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a simple SQL query",
            code = """
                id: flink-sql-query
                type: io.kestra.plugin.flink.SubmitSql
                gatewayConnection:
                  url: "http://flink-sql-gateway:8083"
                statement: |
                  SELECT COUNT(*) as total_orders 
                  FROM orders 
                  WHERE order_date >= CURRENT_DATE - INTERVAL '7' DAY
                """
        ),
        @Example(
            title = "Execute streaming ETL with catalog configuration",
            code = """
                id: flink-streaming-etl
                type: io.kestra.plugin.flink.SubmitSql
                gatewayConnection:
                  url: "http://flink-sql-gateway:8083"
                statement: |
                  INSERT INTO enriched_orders
                  SELECT o.order_id, o.customer_id, c.name, o.amount
                  FROM orders o
                  JOIN customers c ON o.customer_id = c.id
                sessionConfig:
                  catalog: "my_catalog"
                  database: "default"
                  configuration:
                    execution.runtime-mode: "streaming"
                    execution.checkpointing.interval: "30s"
                waitForCompletion: false
                """
        )
    }
)
public class SubmitSql extends Task implements RunnableTask<SubmitSql.Output> {

    @Schema(
        title = "SQL Gateway connection configuration"
    )
    @PluginProperty
    @NotNull
    private SqlGatewayConnection gatewayConnection;

    @Schema(
        title = "SQL statement",
        description = "The SQL statement to execute"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> statement;

    @Schema(
        title = "Session configuration",
        description = "Configuration for the SQL Gateway session"
    )
    @PluginProperty(dynamic = true)
    private Property<Map<String, Object>> sessionConfig;

    @Schema(
        title = "Wait for completion",
        description = "Whether to wait for the SQL statement to complete (only applicable for batch jobs)"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Boolean> waitForCompletion = Property.of(true);

    @Schema(
        title = "Query timeout",
        description = "Maximum time to wait for query completion"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Duration> queryTimeout = Property.of(Duration.ofMinutes(30));

    @Schema(
        title = "Fetch result",
        description = "Whether to fetch and return query results (for SELECT statements)"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Boolean> fetchResult = Property.of(true);

    @Schema(
        title = "Max rows",
        description = "Maximum number of rows to fetch from result set"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Integer> maxRows = Property.of(1000);

    @Override
    public Output run(RunContext runContext) throws Exception {
        // Create a simplified HTTP client for SQL Gateway
        FlinkClient client = FlinkClient.builder()
            .flinkRestUrl(java.net.URI.create(runContext.render(gatewayConnection.getUrl()).as(String.class).orElseThrow()))
            .timeout(runContext.render(gatewayConnection.getTimeout()).as(Duration.class).orElse(Duration.ofMinutes(5)))
            .httpClient(new okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                .build())
            .objectMapper(new ObjectMapper())
            .build();
        
        String statement = runContext.render(this.statement).as(String.class).orElseThrow();
        Boolean waitForCompletion = runContext.render(this.waitForCompletion).as(Boolean.class).orElse(true);
        Boolean fetchResult = runContext.render(this.fetchResult).as(Boolean.class).orElse(true);
        Integer maxRows = runContext.render(this.maxRows).as(Integer.class).orElse(1000);
        
        runContext.logger().info("Executing SQL statement via Flink SQL Gateway");
        
        // Create session
        String sessionId = createSession(client, runContext);
        runContext.logger().info("Created SQL Gateway session: {}", sessionId);
        
        try {
            // Execute statement
            String operationHandle = executeStatement(client, sessionId, statement);
            runContext.logger().info("SQL statement submitted with operation handle: {}", operationHandle);
            
            // Wait for completion if requested
            String status = "PENDING";
            List<Map<String, Object>> resultRows = null;
            
            if (waitForCompletion) {
                status = waitForOperationCompletion(client, sessionId, operationHandle, runContext);
                
                // Fetch results if it's a SELECT statement and completed successfully
                if ("FINISHED".equals(status) && fetchResult && isSelectStatement(statement)) {
                    resultRows = fetchResults(client, sessionId, operationHandle, maxRows);
                    runContext.logger().info("Fetched {} result rows", resultRows != null ? resultRows.size() : 0);
                }
            }
            
            return Output.builder()
                .sessionId(sessionId)
                .operationHandle(operationHandle)
                .status(status)
                .resultRows(resultRows)
                .rowCount(resultRows != null ? resultRows.size() : null)
                .build();
                
        } finally {
            // Close session
            try {
                closeSession(client, sessionId);
                runContext.logger().debug("Closed SQL Gateway session: {}", sessionId);
            } catch (Exception e) {
                runContext.logger().warn("Failed to close session {}: {}", sessionId, e.getMessage());
            }
        }
    }
    
    private String createSession(FlinkClient client, RunContext runContext) throws Exception {
        Map<String, Object> sessionRequest = new HashMap<>();
        
        // Add session configuration if provided
        Map<String, Object> sessionConfig = runContext.render(this.sessionConfig).asMap(String.class, Object.class);
        if (sessionConfig != null) {
            sessionRequest.putAll(sessionConfig);
        }
        
        ObjectMapper mapper = new ObjectMapper();
        String response = client.post("/sessions", mapper.writeValueAsString(sessionRequest));
        
        Map<String, Object> responseMap = mapper.readValue(response, Map.class);
        return (String) responseMap.get("sessionHandle");
    }
    
    private String executeStatement(FlinkClient client, String sessionId, String statement) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("statement", statement);
        
        ObjectMapper mapper = new ObjectMapper();
        String response = client.post("/sessions/" + sessionId + "/statements", 
            mapper.writeValueAsString(request));
        
        Map<String, Object> responseMap = mapper.readValue(response, Map.class);
        return (String) responseMap.get("operationHandle");
    }
    
    private String waitForOperationCompletion(FlinkClient client, String sessionId, 
            String operationHandle, RunContext runContext) throws Exception {
        Duration timeout = runContext.render(this.queryTimeout).as(Duration.class).orElse(Duration.ofMinutes(30));
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        
        ObjectMapper mapper = new ObjectMapper();
        
        while (System.currentTimeMillis() < endTime) {
            String response = client.get("/sessions/" + sessionId + "/operations/" + operationHandle + "/status");
            Map<String, Object> statusMap = mapper.readValue(response, Map.class);
            
            String status = (String) statusMap.get("status");
            
            if ("FINISHED".equals(status) || "CANCELED".equals(status) || "ERROR".equals(status)) {
                if ("ERROR".equals(status)) {
                    String errorMessage = (String) statusMap.get("exception");
                    throw new FlinkException("SQL execution failed: " + errorMessage);
                }
                return status;
            }
            
            Thread.sleep(2000); // Wait 2 seconds before next check
        }
        
        throw new FlinkException("SQL execution timed out after " + timeout);
    }
    
    private List<Map<String, Object>> fetchResults(FlinkClient client, String sessionId, 
            String operationHandle, int maxRows) throws Exception {
        String response = client.get("/sessions/" + sessionId + "/operations/" + operationHandle + 
            "/result/0?maxRows=" + maxRows);
        
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> resultMap = mapper.readValue(response, Map.class);
        
        return (List<Map<String, Object>>) resultMap.get("data");
    }
    
    private void closeSession(FlinkClient client, String sessionId) throws Exception {
        client.post("/sessions/" + sessionId + "/close", "{}");
    }
    
    private boolean isSelectStatement(String statement) {
        return statement.trim().toUpperCase().startsWith("SELECT") ||
               statement.trim().toUpperCase().startsWith("WITH") ||
               statement.trim().toUpperCase().startsWith("SHOW") ||
               statement.trim().toUpperCase().startsWith("DESCRIBE") ||
               statement.trim().toUpperCase().startsWith("EXPLAIN");
    }

    @Builder
    @Getter
    public static class SqlGatewayConnection {
        @Schema(
            title = "SQL Gateway URL",
            description = "The URL of the Flink SQL Gateway, typically http://localhost:8083"
        )
        @PluginProperty(dynamic = true)
        @NotNull
        private Property<String> url;

        @Schema(
            title = "Request timeout",
            description = "Maximum time to wait for API requests to complete"
        )
        @PluginProperty(dynamic = true)
        @Builder.Default
        private Property<Duration> timeout = Property.of(Duration.ofMinutes(5));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Session ID",
            description = "The unique identifier of the SQL Gateway session"
        )
        private String sessionId;
        
        @Schema(
            title = "Operation handle",
            description = "The unique identifier of the SQL operation"
        )
        private String operationHandle;
        
        @Schema(
            title = "Status",
            description = "The final status of the SQL execution"
        )
        private String status;
        
        @Schema(
            title = "Result rows",
            description = "The result rows returned by the query (for SELECT statements)"
        )
        private List<Map<String, Object>> resultRows;
        
        @Schema(
            title = "Row count",
            description = "Number of rows returned"
        )
        private Integer rowCount;
    }
}
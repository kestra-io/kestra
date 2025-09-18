package io.kestra.plugin.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Core client for interacting with Apache Flink clusters via REST API.
 * Supports both standalone and Kubernetes-based Flink deployments.
 */
@Slf4j
@Builder
@Getter
public class FlinkClient {
    
    private static final String DEFAULT_FLINK_REST_PORT = "8081";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    
    private final URI flinkRestUrl;
    private final Duration timeout;
    private final Map<String, String> headers;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    /**
     * Create a FlinkClient from configuration properties
     */
    public static FlinkClient from(RunContext runContext, FlinkConnection connection) throws Exception {
        String baseUrl = runContext.render(connection.getUrl()).as(String.class).orElse(null);
        if (baseUrl == null) {
            throw new IllegalArgumentException("Flink REST URL is required");
        }
        
        Duration timeout = runContext.render(connection.getTimeout()).as(Duration.class).orElse(DEFAULT_TIMEOUT);
        Map<String, String> headers = runContext.render(connection.getHeaders()).asMap(String.class, String.class);
        
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
            .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
            
        // Add custom headers interceptor if headers are provided
        if (headers != null && !headers.isEmpty()) {
            clientBuilder.addInterceptor(chain -> {
                Request.Builder requestBuilder = chain.request().newBuilder();
                headers.forEach(requestBuilder::addHeader);
                return chain.proceed(requestBuilder.build());
            });
        }
        
        return FlinkClient.builder()
            .flinkRestUrl(URI.create(baseUrl))
            .timeout(timeout)
            .headers(headers)
            .httpClient(clientBuilder.build())
            .objectMapper(new ObjectMapper())
            .build();
    }
    
    /**
     * Execute a GET request to Flink REST API
     */
    public String get(String path) throws IOException {
        String url = flinkRestUrl.toString().replaceAll("/$", "") + "/" + path.replaceAll("^/", "");
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();
            
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "No response body";
                throw new FlinkException(String.format("Request failed with status %d: %s", response.code(), body));
            }
            
            return response.body() != null ? response.body().string() : "";
        }
    }
    
    /**
     * Execute a POST request to Flink REST API
     */
    public String post(String path, String jsonBody) throws IOException {
        String url = flinkRestUrl.toString().replaceAll("/$", "") + "/" + path.replaceAll("^/", "");
        
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
            jsonBody,
            okhttp3.MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();
            
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                throw new FlinkException(String.format("Request failed with status %d: %s", response.code(), responseBody));
            }
            
            return response.body() != null ? response.body().string() : "";
        }
    }
    
    /**
     * Execute a PATCH request to Flink REST API
     */
    public String patch(String path, String jsonBody) throws IOException {
        String url = flinkRestUrl.toString().replaceAll("/$", "") + "/" + path.replaceAll("^/", "");
        
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
            jsonBody,
            okhttp3.MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(url)
            .patch(body)
            .build();
            
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                throw new FlinkException(String.format("Request failed with status %d: %s", response.code(), responseBody));
            }
            
            return response.body() != null ? response.body().string() : "";
        }
    }
    
    /**
     * Test connectivity to Flink cluster
     */
    public boolean testConnection() {
        try {
            String response = get("/config");
            return response != null && !response.trim().isEmpty();
        } catch (Exception e) {
            log.error("Failed to connect to Flink cluster at {}: {}", flinkRestUrl, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get Flink cluster overview information
     */
    public Map<String, Object> getOverview() throws IOException {
        String response = get("/overview");
        return objectMapper.readValue(response, Map.class);
    }
}
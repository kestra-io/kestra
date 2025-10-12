package io.kestra.plugin.pipedrive;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Map;

/**
 * HTTP client wrapper for Pipedrive API interactions.
 * Handles authentication via API token and provides standardized error handling.
 */
@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PipedriveClient {
    
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiToken;
    
    /**
     * Creates a new PipedriveClient instance.
     *
     * @param runContext The Kestra run context
     * @param baseUrl    The base URL for Pipedrive API (e.g., "https://api.pipedrive.com/v1")
     * @param apiToken   The Pipedrive API token
     * @return A configured PipedriveClient
     * @throws IllegalVariableEvaluationException if client configuration fails
     */
    public static PipedriveClient of(RunContext runContext, String baseUrl, String apiToken) 
            throws IllegalVariableEvaluationException {
        
        HttpConfiguration config = HttpConfiguration.builder()
            .allowFailed(io.kestra.core.models.property.Property.ofValue(false))
            .build();
        
        HttpClient client = HttpClient.builder()
            .runContext(runContext)
            .configuration(config)
            .build();
        
        String normalizedBaseUrl = baseUrl.endsWith("/") ? 
            baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            
        return new PipedriveClient(client, normalizedBaseUrl, apiToken);
    }
    
    /**
     * Makes a GET request to the Pipedrive API.
     *
     * @param path The API path (e.g., "/persons/1")
     * @return The HTTP response
     * @throws HttpClientException if the request fails
     * @throws IllegalVariableEvaluationException if variable evaluation fails
     */
    public HttpResponse<String> get(String path) throws HttpClientException, IllegalVariableEvaluationException {
        String fullUrl = buildUrl(path);
        
        HttpRequest request = HttpRequest.builder()
            .uri(URI.create(fullUrl))
            .method("GET")
            .build();
        
        log.debug("Making GET request to: {}", fullUrl);
        
        return httpClient.request(request);
    }
    
    /**
     * Makes a POST request to the Pipedrive API with JSON body.
     *
     * @param path The API path (e.g., "/deals")
     * @param body The request body as a Map that will be serialized to JSON
     * @return The HTTP response
     * @throws HttpClientException if the request fails
     * @throws IllegalVariableEvaluationException if variable evaluation fails
     */
    public HttpResponse<String> post(String path, Map<String, Object> body) 
            throws HttpClientException, IllegalVariableEvaluationException {
        return sendWithBody(path, "POST", body);
    }
    
    /**
     * Makes a PUT request to the Pipedrive API with JSON body.
     *
     * @param path The API path (e.g., "/deals/1")
     * @param body The request body as a Map that will be serialized to JSON
     * @return The HTTP response
     * @throws HttpClientException if the request fails
     * @throws IllegalVariableEvaluationException if variable evaluation fails
     */
    public HttpResponse<String> put(String path, Map<String, Object> body) 
            throws HttpClientException, IllegalVariableEvaluationException {
        return sendWithBody(path, "PUT", body);
    }
    
    /**
     * Makes a DELETE request to the Pipedrive API.
     *
     * @param path The API path (e.g., "/deals/1")
     * @return The HTTP response
     * @throws HttpClientException if the request fails
     * @throws IllegalVariableEvaluationException if variable evaluation fails
     */
    public HttpResponse<String> delete(String path) throws HttpClientException, IllegalVariableEvaluationException {
        String fullUrl = buildUrl(path);
        
        HttpRequest request = HttpRequest.builder()
            .uri(URI.create(fullUrl))
            .method("DELETE")
            .build();
        
        log.debug("Making DELETE request to: {}", fullUrl);
        
        return httpClient.request(request);
    }
    
    private HttpResponse<String> sendWithBody(String path, String method, Map<String, Object> body) 
            throws HttpClientException, IllegalVariableEvaluationException {
        String fullUrl = buildUrl(path);
        
        try {
            String jsonBody = JacksonMapper.ofJson().writeValueAsString(body);
            
            HttpRequest request = HttpRequest.builder()
                .uri(URI.create(fullUrl))
                .method(method)
                .body(HttpRequest.JsonRequestBody.builder()
                    .content(body)
                    .build())
                .build();
            
            log.debug("Making {} request to: {} with body: {}", method, fullUrl, jsonBody);
            
            return httpClient.request(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body to JSON", e);
        }
    }
    
    private String buildUrl(String path) {
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        String separator = cleanPath.contains("?") ? "&" : "?";
        return baseUrl + cleanPath + separator + "api_token=" + apiToken;
    }
}
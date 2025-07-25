package io.kestra.plugin.core.supabase;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for Supabase tasks providing common functionality.
 */
@Slf4j
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractSupabase extends Task implements SupabaseInterface {
    
    @NotNull
    protected Property<String> url;

    @NotNull
    protected Property<String> apiKey;

    @Builder.Default
    protected Property<String> schema = Property.ofValue("public");

    @Builder.Default
    protected HttpConfiguration options = HttpConfiguration.builder().build();

    /**
     * Creates an HTTP client configured for Supabase API calls.
     */
    protected HttpClient client(RunContext runContext) throws IllegalVariableEvaluationException, MalformedURLException, URISyntaxException {
        return HttpClient.builder()
            .configuration(this.options)
            .runContext(runContext)
            .build();
    }

    /**
     * Creates a base HTTP request with common Supabase headers.
     */
    protected HttpRequest.HttpRequestBuilder baseRequest(RunContext runContext, String endpoint) throws IllegalVariableEvaluationException, URISyntaxException {
        String renderedUrl = runContext.render(this.url).as(String.class).orElseThrow();
        String renderedApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();
        String renderedSchema = runContext.render(this.schema).as(String.class).orElse("public");
        
        // Ensure URL ends with /rest/v1 for the REST API
        if (!renderedUrl.endsWith("/rest/v1")) {
            if (renderedUrl.endsWith("/")) {
                renderedUrl = renderedUrl + "rest/v1";
            } else {
                renderedUrl = renderedUrl + "/rest/v1";
            }
        }
        
        String fullUrl = renderedUrl + endpoint;
        
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("apikey", List.of(renderedApiKey));
        headers.put("Authorization", List.of("Bearer " + renderedApiKey));
        headers.put("Content-Type", List.of("application/json"));
        headers.put("Accept", List.of("application/json"));

        // Add schema header if not using the default public schema
        if (!"public".equals(renderedSchema)) {
            headers.put("Accept-Profile", List.of(renderedSchema));
            headers.put("Content-Profile", List.of(renderedSchema));
        }

        return HttpRequest.builder()
            .uri(new URI(fullUrl))
            .headers(HttpHeaders.of(headers, (a, b) -> true));
    }

    /**
     * Builds the REST API endpoint for a table.
     */
    protected String buildTableEndpoint(String tableName) {
        return "/" + tableName;
    }

    /**
     * Builds the RPC endpoint for stored procedures.
     */
    protected String buildRpcEndpoint(String functionName) {
        return "/rpc/" + functionName;
    }
}

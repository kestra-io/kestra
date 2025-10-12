package io.kestra.plugin.pipedrive.tasks;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.pipedrive.PipedriveClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search for deals in Pipedrive",
    description = "Search and retrieve multiple deals from your Pipedrive account based on various criteria."
)
@Plugin(
    examples = {
        @Example(
            title = "Search deals by term",
            full = true,
            code = """
                id: search_pipedrive_deals
                namespace: company.team

                tasks:
                  - id: search_deals
                    type: io.kestra.plugin.pipedrive.tasks.DealsSearch
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    term: "Enterprise"
                    limit: 50
                """
        ),
        @Example(
            title = "List all deals with pagination",
            full = true,
            code = """
                id: list_all_deals
                namespace: company.team

                tasks:
                  - id: list_deals
                    type: io.kestra.plugin.pipedrive.tasks.DealsSearch
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    limit: 100
                    start: 0
                """
        ),
        @Example(
            title = "Search deals by status and stage",
            full = true,
            code = """
                id: search_open_deals
                namespace: company.team

                tasks:
                  - id: search_deals
                    type: io.kestra.plugin.pipedrive.tasks.DealsSearch
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    status: "open"
                    stageId: 2
                    limit: 25
                """
        )
    }
)
public class DealsSearch extends Task implements RunnableTask<DealsSearch.Output> {
    
    @NotNull
    @Schema(
        title = "Pipedrive API Token",
        description = "Your Pipedrive API token. Store this in Kestra's secret management."
    )
    private Property<String> apiToken;

    @Schema(
        title = "Pipedrive API Base URL",
        description = "The base URL for Pipedrive API calls. Defaults to https://api.pipedrive.com/v1"
    )
    @Builder.Default
    private Property<String> baseUrl = Property.ofValue("https://api.pipedrive.com/v1");

    @Schema(
        title = "Search Term",
        description = "Search term to look for in deal titles. If not provided, returns all deals (with pagination)."
    )
    private Property<String> term;

    @Schema(
        title = "Deal Status",
        description = "Filter deals by status. Can be 'open', 'won', 'lost', or 'deleted'."
    )
    private Property<String> status;

    @Schema(
        title = "Stage ID",
        description = "Filter deals by stage ID."
    )
    private Property<Integer> stageId;

    @Schema(
        title = "Person ID",
        description = "Filter deals by person ID."
    )
    private Property<Integer> personId;

    @Schema(
        title = "Organization ID", 
        description = "Filter deals by organization ID."
    )
    private Property<Integer> organizationId;

    @Schema(
        title = "Limit",
        description = "Number of items to return (max 500). Defaults to 100."
    )
    @Builder.Default
    private Property<Integer> limit = Property.ofValue(100);

    @Schema(
        title = "Start",
        description = "Pagination start offset. Defaults to 0."
    )
    @Builder.Default
    private Property<Integer> start = Property.ofValue(0);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String token = runContext.render(apiToken).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("API token is required"));
        String baseUrlValue = runContext.render(baseUrl).as(String.class).orElse("https://api.pipedrive.com/v1");

        PipedriveClient client = PipedriveClient.of(runContext, baseUrlValue, token);

        // Build query parameters
        StringBuilder queryParams = new StringBuilder();
        
        // Add pagination parameters
        int limitValue = runContext.render(limit).as(Integer.class).orElse(100);
        int startValue = runContext.render(start).as(Integer.class).orElse(0);
        queryParams.append("limit=").append(limitValue);
        queryParams.append("&start=").append(startValue);

        // Add optional filters
        runContext.render(status).as(String.class).ifPresent(s -> 
            queryParams.append("&status=").append(URLEncoder.encode(s, StandardCharsets.UTF_8)));
        runContext.render(stageId).as(Integer.class).ifPresent(sid -> 
            queryParams.append("&stage_id=").append(sid));
        runContext.render(personId).as(Integer.class).ifPresent(pid -> 
            queryParams.append("&person_id=").append(pid));
        runContext.render(organizationId).as(Integer.class).ifPresent(oid -> 
            queryParams.append("&org_id=").append(oid));

        String endpoint;
        String termValue = runContext.render(term).as(String.class).orElse(null);
        
        if (termValue != null && !termValue.trim().isEmpty()) {
            // Use search endpoint if search term is provided
            endpoint = "/deals/search?" + queryParams.toString() + "&term=" + URLEncoder.encode(termValue, StandardCharsets.UTF_8);
        } else {
            // Use list endpoint if no search term
            endpoint = "/deals?" + queryParams.toString();
        }

        try {
            HttpResponse<String> response = client.get(endpoint);
            
            runContext.logger().info("Deals search completed successfully");
            
            // Parse the response to extract deals information
            Map<String, Object> responseData = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);
            List<Map<String, Object>> dealsData = (List<Map<String, Object>>) responseData.get("data");
            
            Map<String, Object> additionalData = (Map<String, Object>) responseData.get("additional_data");
            Map<String, Object> pagination = additionalData != null ? 
                (Map<String, Object>) additionalData.get("pagination") : null;
                
            Integer totalCount = null;
            Boolean hasMore = null;
            
            if (pagination != null) {
                totalCount = (Integer) pagination.get("total_count");
                hasMore = (Boolean) pagination.get("more_items_in_collection");
            }
            
            return DealsSearch.Output.builder()
                .deals(dealsData)
                .totalCount(totalCount)
                .hasMore(hasMore)
                .limit(limitValue)
                .start(startValue)
                .count(dealsData != null ? dealsData.size() : 0)
                .response(response.getBody())
                .build();
                
        } catch (HttpClientException e) {
            throw new RuntimeException("Failed to search deals in Pipedrive: " + e.getMessage(), e);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException("Failed to evaluate variables: " + e.getMessage(), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Deals",
            description = "List of deals returned by the search."
        )
        private final List<Map<String, Object>> deals;

        @Schema(
            title = "Total Count",
            description = "Total number of deals matching the search criteria."
        )
        private final Integer totalCount;

        @Schema(
            title = "Has More",
            description = "Whether there are more results available."
        )
        private final Boolean hasMore;

        @Schema(
            title = "Limit",
            description = "The limit used in this request."
        )
        private final Integer limit;

        @Schema(
            title = "Start",
            description = "The start offset used in this request."
        )
        private final Integer start;

        @Schema(
            title = "Count",
            description = "Number of deals returned in this response."
        )
        private final Integer count;

        @Schema(
            title = "API Response",
            description = "Full response from the Pipedrive API."
        )
        private final String response;
    }
}
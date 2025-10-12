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

import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a new deal in Pipedrive",
    description = "Create a new deal with the specified properties in your Pipedrive account."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a basic deal",
            full = true,
            code = """
                id: create_pipedrive_deal
                namespace: company.team

                tasks:
                  - id: create_deal
                    type: io.kestra.plugin.pipedrive.tasks.DealsCreate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    title: "New Sales Opportunity"
                    value: 5000
                    currency: "USD"
                """
        ),
        @Example(
            title = "Create a deal with all properties",
            full = true,
            code = """
                id: create_complete_deal
                namespace: company.team

                tasks:
                  - id: create_deal
                    type: io.kestra.plugin.pipedrive.tasks.DealsCreate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    title: "Enterprise Sale - Q1 2025"
                    value: 25000
                    currency: "USD"
                    status: "open"
                    probability: 75
                    personId: 123
                    organizationId: 456
                    stageId: 2
                    expectedCloseDate: "2025-03-31"
                """
        )
    }
)
public class DealsCreate extends Task implements RunnableTask<DealsCreate.Output> {
    
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

    @NotNull
    @Schema(
        title = "Deal Title",
        description = "The title of the deal."
    )
    private Property<String> title;

    @Schema(
        title = "Deal Value",
        description = "Monetary value of the deal."
    )
    private Property<Integer> value;

    @Schema(
        title = "Currency",
        description = "Currency of the deal. 3-letter ISO code (e.g., USD, EUR)."
    )
    private Property<String> currency;

    @Schema(
        title = "Deal Status",
        description = "Status of the deal. Can be 'open', 'won', or 'lost'."
    )
    private Property<String> status;

    @Schema(
        title = "Probability",
        description = "Deal success probability percentage (0-100)."
    )
    private Property<Integer> probability;

    @Schema(
        title = "Person ID",
        description = "ID of the person this deal is associated with."
    )
    private Property<Integer> personId;

    @Schema(
        title = "Organization ID", 
        description = "ID of the organization this deal is associated with."
    )
    private Property<Integer> organizationId;

    @Schema(
        title = "Stage ID",
        description = "ID of the stage this deal should be placed in."
    )
    private Property<Integer> stageId;

    @Schema(
        title = "Expected Close Date",
        description = "Expected close date in YYYY-MM-DD format."
    )
    private Property<String> expectedCloseDate;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String token = runContext.render(apiToken).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("API token is required"));
        String baseUrlValue = runContext.render(baseUrl).as(String.class).orElse("https://api.pipedrive.com/v1");
        String titleValue = runContext.render(title).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("Deal title is required"));

        PipedriveClient client = PipedriveClient.of(runContext, baseUrlValue, token);

        Map<String, Object> dealData = new HashMap<>();
        dealData.put("title", titleValue);

        // Add optional fields if provided
        runContext.render(value).as(Integer.class).ifPresent(v -> dealData.put("value", v));
        runContext.render(currency).as(String.class).ifPresent(c -> dealData.put("currency", c));
        runContext.render(status).as(String.class).ifPresent(s -> dealData.put("status", s));
        runContext.render(probability).as(Integer.class).ifPresent(p -> dealData.put("probability", p));
        runContext.render(personId).as(Integer.class).ifPresent(pid -> dealData.put("person_id", pid));
        runContext.render(organizationId).as(Integer.class).ifPresent(oid -> dealData.put("org_id", oid));
        runContext.render(stageId).as(Integer.class).ifPresent(sid -> dealData.put("stage_id", sid));
        runContext.render(expectedCloseDate).as(String.class).ifPresent(ecd -> dealData.put("expected_close_date", ecd));

        try {
            HttpResponse<String> response = client.post("/deals", dealData);
            
            runContext.logger().info("Deal created successfully. Response: {}", response.getBody());
            
            // Parse the response to extract deal information
            Map<String, Object> responseData = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);
            Map<String, Object> dealInfo = (Map<String, Object>) responseData.get("data");
            
            Integer dealId = (Integer) dealInfo.get("id");
            
            return DealsCreate.Output.builder()
                .dealId(dealId)
                .response(response.getBody())
                .build();
                
        } catch (HttpClientException e) {
            throw new RuntimeException("Failed to create deal in Pipedrive: " + e.getMessage(), e);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException("Failed to evaluate variables: " + e.getMessage(), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Deal ID",
            description = "The ID of the created deal."
        )
        private final Integer dealId;

        @Schema(
            title = "API Response",
            description = "Full response from the Pipedrive API."
        )
        private final String response;
    }
}
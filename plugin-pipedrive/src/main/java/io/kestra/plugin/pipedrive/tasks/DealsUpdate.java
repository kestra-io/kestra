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
    title = "Update an existing deal in Pipedrive",
    description = "Update properties of an existing deal in your Pipedrive account."
)
@Plugin(
    examples = {
        @Example(
            title = "Update deal value and stage",
            full = true,
            code = """
                id: update_pipedrive_deal
                namespace: company.team

                tasks:
                  - id: update_deal
                    type: io.kestra.plugin.pipedrive.tasks.DealsUpdate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    dealId: 123
                    value: 7500
                    stageId: 3
                    status: "won"
                """
        ),
        @Example(
            title = "Update deal title and probability",
            full = true,
            code = """
                id: update_deal_progress
                namespace: company.team

                tasks:
                  - id: update_deal
                    type: io.kestra.plugin.pipedrive.tasks.DealsUpdate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    dealId: "{{ outputs.create_deal.dealId }}"
                    title: "Updated Deal Title - Almost Closed"
                    probability: 90
                    expectedCloseDate: "2025-02-15"
                """
        )
    }
)
public class DealsUpdate extends Task implements RunnableTask<DealsUpdate.Output> {
    
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
        title = "Deal ID",
        description = "The ID of the deal to update."
    )
    private Property<Integer> dealId;

    @Schema(
        title = "Deal Title",
        description = "The updated title of the deal."
    )
    private Property<String> title;

    @Schema(
        title = "Deal Value",
        description = "Updated monetary value of the deal."
    )
    private Property<Integer> value;

    @Schema(
        title = "Currency",
        description = "Updated currency of the deal. 3-letter ISO code (e.g., USD, EUR)."
    )
    private Property<String> currency;

    @Schema(
        title = "Deal Status",
        description = "Updated status of the deal. Can be 'open', 'won', or 'lost'."
    )
    private Property<String> status;

    @Schema(
        title = "Probability",
        description = "Updated deal success probability percentage (0-100)."
    )
    private Property<Integer> probability;

    @Schema(
        title = "Person ID",
        description = "Updated ID of the person this deal is associated with."
    )
    private Property<Integer> personId;

    @Schema(
        title = "Organization ID", 
        description = "Updated ID of the organization this deal is associated with."
    )
    private Property<Integer> organizationId;

    @Schema(
        title = "Stage ID",
        description = "Updated ID of the stage this deal should be placed in."
    )
    private Property<Integer> stageId;

    @Schema(
        title = "Expected Close Date",
        description = "Updated expected close date in YYYY-MM-DD format."
    )
    private Property<String> expectedCloseDate;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String token = runContext.render(apiToken).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("API token is required"));
        String baseUrlValue = runContext.render(baseUrl).as(String.class).orElse("https://api.pipedrive.com/v1");
        Integer dealIdValue = runContext.render(dealId).as(Integer.class).orElseThrow(() ->
            new IllegalArgumentException("Deal ID is required"));

        PipedriveClient client = PipedriveClient.of(runContext, baseUrlValue, token);

        Map<String, Object> updateData = new HashMap<>();

        // Add fields to update only if provided
        runContext.render(title).as(String.class).ifPresent(t -> updateData.put("title", t));
        runContext.render(value).as(Integer.class).ifPresent(v -> updateData.put("value", v));
        runContext.render(currency).as(String.class).ifPresent(c -> updateData.put("currency", c));
        runContext.render(status).as(String.class).ifPresent(s -> updateData.put("status", s));
        runContext.render(probability).as(Integer.class).ifPresent(p -> updateData.put("probability", p));
        runContext.render(personId).as(Integer.class).ifPresent(pid -> updateData.put("person_id", pid));
        runContext.render(organizationId).as(Integer.class).ifPresent(oid -> updateData.put("org_id", oid));
        runContext.render(stageId).as(Integer.class).ifPresent(sid -> updateData.put("stage_id", sid));
        runContext.render(expectedCloseDate).as(String.class).ifPresent(ecd -> updateData.put("expected_close_date", ecd));

        if (updateData.isEmpty()) {
            throw new IllegalArgumentException("At least one field must be provided for update");
        }

        try {
            HttpResponse<String> response = client.put("/deals/" + dealIdValue, updateData);
            
            runContext.logger().info("Deal {} updated successfully. Response: {}", dealIdValue, response.getBody());
            
            // Parse the response to extract deal information
            Map<String, Object> responseData = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);
            Map<String, Object> dealInfo = (Map<String, Object>) responseData.get("data");
            
            return DealsUpdate.Output.builder()
                .dealId(dealIdValue)
                .updated(true)
                .response(response.getBody())
                .build();
                
        } catch (HttpClientException e) {
            throw new RuntimeException("Failed to update deal in Pipedrive: " + e.getMessage(), e);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException("Failed to evaluate variables: " + e.getMessage(), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Deal ID",
            description = "The ID of the updated deal."
        )
        private final Integer dealId;

        @Schema(
            title = "Updated",
            description = "Whether the deal was successfully updated."
        )
        private final Boolean updated;

        @Schema(
            title = "API Response",
            description = "Full response from the Pipedrive API."
        )
        private final String response;
    }
}
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

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a specific deal from Pipedrive",
    description = "Retrieve detailed information about a specific deal from your Pipedrive account."
)
@Plugin(
    examples = {
        @Example(
            title = "Get deal by ID",
            full = true,
            code = """
                id: get_pipedrive_deal
                namespace: company.team

                tasks:
                  - id: get_deal
                    type: io.kestra.plugin.pipedrive.tasks.DealsGet
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    dealId: 123
                """
        ),
        @Example(
            title = "Get deal created in previous task",
            full = true,
            code = """
                id: get_created_deal
                namespace: company.team

                tasks:
                  - id: create_deal
                    type: io.kestra.plugin.pipedrive.tasks.DealsCreate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    title: "Test Deal"
                    value: 1000

                  - id: get_deal
                    type: io.kestra.plugin.pipedrive.tasks.DealsGet
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    dealId: "{{ outputs.create_deal.dealId }}"
                """
        )
    }
)
public class DealsGet extends Task implements RunnableTask<DealsGet.Output> {

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
        description = "The ID of the deal to retrieve."
    )
    private Property<Integer> dealId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String token = runContext.render(apiToken).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("API token is required"));
        String baseUrlValue = runContext.render(baseUrl).as(String.class).orElse("https://api.pipedrive.com/v1");
        Integer dealIdValue = runContext.render(dealId).as(Integer.class).orElseThrow(() ->
            new IllegalArgumentException("Deal ID is required"));

        PipedriveClient client = PipedriveClient.of(runContext, baseUrlValue, token);

        try {
            HttpResponse<String> response = client.get("/deals/" + dealIdValue);

            runContext.logger().info("Deal {} retrieved successfully", dealIdValue);

            // Parse the response to extract deal information
            Map<String, Object> responseData = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);
            Map<String, Object> dealData = (Map<String, Object>) responseData.get("data");

            return Output.builder()
                .dealId(dealIdValue)
                .title((String) dealData.get("title"))
                .value((Integer) dealData.get("value"))
                .currency((String) dealData.get("currency"))
                .status((String) dealData.get("status"))
                .stageId((Integer) dealData.get("stage_id"))
                .personId((Integer) dealData.get("person_id"))
                .organizationId((Integer) dealData.get("org_id"))
                .response(response.getBody())
                .build();

        } catch (HttpClientException e) {
            throw new RuntimeException("Failed to get deal from Pipedrive: " + e.getMessage(), e);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException("Failed to evaluate variables: " + e.getMessage(), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Deal ID",
            description = "The ID of the retrieved deal."
        )
        private final Integer dealId;

        @Schema(
            title = "Deal Title",
            description = "The title of the deal."
        )
        private final String title;

        @Schema(
            title = "Deal Value",
            description = "The monetary value of the deal."
        )
        private final Integer value;

        @Schema(
            title = "Currency",
            description = "The currency of the deal."
        )
        private final String currency;

        @Schema(
            title = "Status",
            description = "The status of the deal."
        )
        private final String status;

        @Schema(
            title = "Stage ID",
            description = "The stage ID of the deal."
        )
        private final Integer stageId;

        @Schema(
            title = "Person ID",
            description = "The person ID associated with the deal."
        )
        private final Integer personId;

        @Schema(
            title = "Organization ID",
            description = "The organization ID associated with the deal."
        )
        private final Integer organizationId;

        @Schema(
            title = "API Response",
            description = "Full response from the Pipedrive API."
        )
        private final String response;
    }
}
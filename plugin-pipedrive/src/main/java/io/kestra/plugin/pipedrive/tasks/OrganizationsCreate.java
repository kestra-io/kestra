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
    title = "Create a new organization in Pipedrive",
    description = "Create a new organization with the specified properties in your Pipedrive account."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a basic organization",
            full = true,
            code = """
                id: create_pipedrive_organization
                namespace: company.team

                tasks:
                  - id: create_organization
                    type: io.kestra.plugin.pipedrive.tasks.OrganizationsCreate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    name: "ACME Corporation"
                """
        ),
        @Example(
            title = "Create organization with details",
            full = true,
            code = """
                id: create_detailed_organization
                namespace: company.team

                tasks:
                  - id: create_organization
                    type: io.kestra.plugin.pipedrive.tasks.OrganizationsCreate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    name: "Tech Innovations Ltd"
                    address: "123 Business St, Tech City"
                    ownerId: 123
                    visible: "3"
                """
        )
    }
)
public class OrganizationsCreate extends Task implements RunnableTask<OrganizationsCreate.Output> {
    
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
        title = "Organization Name",
        description = "The name of the organization."
    )
    private Property<String> name;

    @Schema(
        title = "Address",
        description = "The address of the organization."
    )
    private Property<String> address;

    @Schema(
        title = "Owner ID",
        description = "The ID of the user who will be the owner of this organization."
    )
    private Property<Integer> ownerId;

    @Schema(
        title = "Visibility",
        description = "Visibility of the organization. 1 = Owner & followers (private), 2 = Entire company (shared), 3 = Owner, followers & everyone (public)."
    )
    private Property<String> visible;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String token = runContext.render(apiToken).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("API token is required"));
        String baseUrlValue = runContext.render(baseUrl).as(String.class).orElse("https://api.pipedrive.com/v1");
        String nameValue = runContext.render(name).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("Organization name is required"));

        PipedriveClient client = PipedriveClient.of(runContext, baseUrlValue, token);

        Map<String, Object> organizationData = new HashMap<>();
        organizationData.put("name", nameValue);

        // Add optional fields if provided
        runContext.render(address).as(String.class).ifPresent(a -> organizationData.put("address", a));
        runContext.render(ownerId).as(Integer.class).ifPresent(oid -> organizationData.put("owner_id", oid));
        runContext.render(visible).as(String.class).ifPresent(v -> organizationData.put("visible_to", v));

        try {
            HttpResponse<String> response = client.post("/organizations", organizationData);
            
            runContext.logger().info("Organization created successfully. Response: {}", response.getBody());
            
            // Parse the response to extract organization information
            Map<String, Object> responseData = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);
            Map<String, Object> orgInfo = (Map<String, Object>) responseData.get("data");
            
            Integer organizationId = (Integer) orgInfo.get("id");
            
            return OrganizationsCreate.Output.builder()
                .organizationId(organizationId)
                .name(nameValue)
                .response(response.getBody())
                .build();
                
        } catch (HttpClientException e) {
            throw new RuntimeException("Failed to create organization in Pipedrive: " + e.getMessage(), e);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException("Failed to evaluate variables: " + e.getMessage(), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Organization ID",
            description = "The ID of the created organization."
        )
        private final Integer organizationId;

        @Schema(
            title = "Name",
            description = "The name of the created organization."
        )
        private final String name;

        @Schema(
            title = "API Response",
            description = "Full response from the Pipedrive API."
        )
        private final String response;
    }
}
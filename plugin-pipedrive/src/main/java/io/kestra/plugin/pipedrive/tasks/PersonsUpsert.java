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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create or update a person in Pipedrive by email",
    description = "Create a new person or update an existing person in your Pipedrive account. If a person with the given email already exists, it will be updated; otherwise, a new person will be created."
)
@Plugin(
    examples = {
        @Example(
            title = "Create or update a person",
            full = true,
            code = """
                id: upsert_person
                namespace: company.team

                tasks:
                  - id: upsert_person
                    type: io.kestra.plugin.pipedrive.tasks.PersonsUpsert
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    name: "John Doe"
                    email: "john.doe@company.com"
                    phone: "+1234567890"
                """
        ),
        @Example(
            title = "Create person with organization",
            full = true,
            code = """
                id: upsert_person_with_org
                namespace: company.team

                tasks:
                  - id: upsert_person
                    type: io.kestra.plugin.pipedrive.tasks.PersonsUpsert
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    name: "Jane Smith"
                    email: "jane.smith@enterprise.com"
                    organizationId: 456
                    jobTitle: "Sales Manager"
                """
        )
    }
)
public class PersonsUpsert extends Task implements RunnableTask<PersonsUpsert.Output> {
    
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
        title = "Person Name",
        description = "The name of the person."
    )
    private Property<String> name;

    @NotNull
    @Schema(
        title = "Email Address",
        description = "The email address of the person. This will be used to check if the person already exists."
    )
    private Property<String> email;

    @Schema(
        title = "Phone Number",
        description = "The phone number of the person."
    )
    private Property<String> phone;

    @Schema(
        title = "Organization ID",
        description = "The ID of the organization this person belongs to."
    )
    private Property<Integer> organizationId;

    @Schema(
        title = "Job Title",
        description = "The job title of the person."
    )
    private Property<String> jobTitle;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String token = runContext.render(apiToken).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("API token is required"));
        String baseUrlValue = runContext.render(baseUrl).as(String.class).orElse("https://api.pipedrive.com/v1");
        String nameValue = runContext.render(name).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("Person name is required"));
        String emailValue = runContext.render(email).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("Email address is required"));

        PipedriveClient client = PipedriveClient.of(runContext, baseUrlValue, token);

        try {
            // First, search for existing person with this email
            String encodedEmail = URLEncoder.encode(emailValue, StandardCharsets.UTF_8);
            String searchEndpoint = "/persons/search?term=" + encodedEmail + "&fields=email&exact_match=true";
            
            HttpResponse<String> searchResponse = client.get(searchEndpoint);
            Map<String, Object> searchResponseData = JacksonMapper.ofJson().readValue(searchResponse.getBody(), Map.class);
            List<Map<String, Object>> searchResults = (List<Map<String, Object>>) searchResponseData.get("data");
            
            Map<String, Object> existingPerson = null;
            Integer existingPersonId = null;
            
            // Find person with matching email
            if (searchResults != null && !searchResults.isEmpty()) {
                for (Map<String, Object> personData : searchResults) {
                    List<Map<String, Object>> emails = (List<Map<String, Object>>) personData.get("emails");
                    if (emails != null) {
                        for (Map<String, Object> emailData : emails) {
                            String emailAddress = (String) emailData.get("value");
                            if (emailValue.equalsIgnoreCase(emailAddress)) {
                                existingPerson = personData;
                                existingPersonId = (Integer) personData.get("id");
                                break;
                            }
                        }
                        if (existingPerson != null) break;
                    }
                }
            }

            // Prepare person data
            Map<String, Object> personData = new HashMap<>();
            personData.put("name", nameValue);
            personData.put("email", Arrays.asList(emailValue));

            // Add optional fields if provided
            runContext.render(phone).as(String.class).ifPresent(p -> 
                personData.put("phone", Arrays.asList(p)));
            runContext.render(organizationId).as(Integer.class).ifPresent(oid -> 
                personData.put("org_id", oid));
            runContext.render(jobTitle).as(String.class).ifPresent(jt -> 
                personData.put("job_title", jt));

            HttpResponse<String> response;
            boolean isUpdate = existingPersonId != null;
            
            if (isUpdate) {
                // Update existing person
                response = client.put("/persons/" + existingPersonId, personData);
                runContext.logger().info("Person {} updated successfully", existingPersonId);
            } else {
                // Create new person
                response = client.post("/persons", personData);
                runContext.logger().info("New person created successfully");
            }
            
            // Parse the response to extract person information
            Map<String, Object> responseData = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);
            Map<String, Object> personInfo = (Map<String, Object>) responseData.get("data");
            
            Integer personId = (Integer) personInfo.get("id");
            
            return PersonsUpsert.Output.builder()
                .personId(personId)
                .created(!isUpdate)
                .updated(isUpdate)
                .name(nameValue)
                .email(emailValue)
                .response(response.getBody())
                .build();
                
        } catch (HttpClientException e) {
            throw new RuntimeException("Failed to upsert person in Pipedrive: " + e.getMessage(), e);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException("Failed to evaluate variables: " + e.getMessage(), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Person ID",
            description = "The ID of the created or updated person."
        )
        private final Integer personId;

        @Schema(
            title = "Created",
            description = "Whether a new person was created."
        )
        private final Boolean created;

        @Schema(
            title = "Updated",
            description = "Whether an existing person was updated."
        )
        private final Boolean updated;

        @Schema(
            title = "Name",
            description = "The name of the person."
        )
        private final String name;

        @Schema(
            title = "Email",
            description = "The email address of the person."
        )
        private final String email;

        @Schema(
            title = "API Response",
            description = "Full response from the Pipedrive API."
        )
        private final String response;
    }
}
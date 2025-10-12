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
    title = "Get a person from Pipedrive by email address",
    description = "Search for and retrieve a person from your Pipedrive account using their email address."
)
@Plugin(
    examples = {
        @Example(
            title = "Get person by email",
            full = true,
            code = """
                id: get_person_by_email
                namespace: company.team

                tasks:
                  - id: get_person
                    type: io.kestra.plugin.pipedrive.tasks.PersonsGetByEmail
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    email: "john.doe@company.com"
                """
        ),
        @Example(
            title = "Get person with exact email match",
            full = true,
            code = """
                id: get_person_exact_match
                namespace: company.team

                tasks:
                  - id: get_person
                    type: io.kestra.plugin.pipedrive.tasks.PersonsGetByEmail
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    email: "{{ inputs.customer_email }}"
                    exactMatch: true
                """
        )
    }
)
public class PersonsGetByEmail extends Task implements RunnableTask<PersonsGetByEmail.Output> {
    
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
        title = "Email Address",
        description = "The email address to search for."
    )
    private Property<String> email;

    @Schema(
        title = "Exact Match",
        description = "If true, only returns exact email matches. If false, returns partial matches too. Defaults to true."
    )
    @Builder.Default
    private Property<Boolean> exactMatch = Property.ofValue(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String token = runContext.render(apiToken).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("API token is required"));
        String baseUrlValue = runContext.render(baseUrl).as(String.class).orElse("https://api.pipedrive.com/v1");
        String emailValue = runContext.render(email).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("Email address is required"));
        Boolean exactMatchValue = runContext.render(exactMatch).as(Boolean.class).orElse(true);

        PipedriveClient client = PipedriveClient.of(runContext, baseUrlValue, token);

        try {
            // Use the search endpoint to find persons by email
            String encodedEmail = URLEncoder.encode(emailValue, StandardCharsets.UTF_8);
            String endpoint = "/persons/search?term=" + encodedEmail + "&fields=email&exact_match=" + exactMatchValue;
            
            HttpResponse<String> response = client.get(endpoint);
            
            runContext.logger().info("Person search for email {} completed", emailValue);
            
            // Parse the response to extract person information
            Map<String, Object> responseData = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);
            List<Map<String, Object>> personsData = (List<Map<String, Object>>) responseData.get("data");
            
            Map<String, Object> person = null;
            Integer personId = null;
            String personName = null;
            String personEmail = null;
            Integer organizationId = null;
            
            // Find the person with the matching email
            if (personsData != null && !personsData.isEmpty()) {
                for (Map<String, Object> personData : personsData) {
                    List<Map<String, Object>> emails = (List<Map<String, Object>>) personData.get("emails");
                    if (emails != null) {
                        for (Map<String, Object> emailData : emails) {
                            String emailAddress = (String) emailData.get("value");
                            if (exactMatchValue) {
                                if (emailValue.equalsIgnoreCase(emailAddress)) {
                                    person = personData;
                                    break;
                                }
                            } else {
                                if (emailAddress != null && emailAddress.toLowerCase().contains(emailValue.toLowerCase())) {
                                    person = personData;
                                    break;
                                }
                            }
                        }
                        if (person != null) break;
                    }
                }
                
                if (person != null) {
                    personId = (Integer) person.get("id");
                    personName = (String) person.get("name");
                    // Get first email from the person's email list
                    List<Map<String, Object>> emails = (List<Map<String, Object>>) person.get("emails");
                    if (emails != null && !emails.isEmpty()) {
                        personEmail = (String) emails.get(0).get("value");
                    }
                    organizationId = (Integer) person.get("organization_id");
                }
            }
            
            return PersonsGetByEmail.Output.builder()
                .found(person != null)
                .personId(personId)
                .name(personName)
                .email(personEmail)
                .organizationId(organizationId)
                .person(person)
                .response(response.getBody())
                .build();
                
        } catch (HttpClientException e) {
            throw new RuntimeException("Failed to search person in Pipedrive: " + e.getMessage(), e);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException("Failed to evaluate variables: " + e.getMessage(), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Found",
            description = "Whether a person with the given email was found."
        )
        private final Boolean found;

        @Schema(
            title = "Person ID",
            description = "The ID of the found person (null if not found)."
        )
        private final Integer personId;

        @Schema(
            title = "Name",
            description = "The name of the found person (null if not found)."
        )
        private final String name;

        @Schema(
            title = "Email",
            description = "The email address of the found person (null if not found)."
        )
        private final String email;

        @Schema(
            title = "Organization ID",
            description = "The organization ID of the found person (null if not found or no organization)."
        )
        private final Integer organizationId;

        @Schema(
            title = "Person Data",
            description = "Full person data object from Pipedrive (null if not found)."
        )
        private final Map<String, Object> person;

        @Schema(
            title = "API Response",
            description = "Full response from the Pipedrive API."
        )
        private final String response;
    }
}
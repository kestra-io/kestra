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
    title = "Create a new activity in Pipedrive",
    description = "Create a new activity (task, call, meeting, etc.) in your Pipedrive account."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a meeting activity",
            full = true,
            code = """
                id: create_pipedrive_activity
                namespace: company.team

                tasks:
                  - id: create_activity
                    type: io.kestra.plugin.pipedrive.tasks.ActivitiesCreate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    baseUrl: "https://api.pipedrive.com/v1"
                    subject: "Product Demo Meeting"
                    type: "meeting"
                    dueDate: "2025-01-15"
                    dueTime: "14:30"
                    duration: "01:00"
                """
        ),
        @Example(
            title = "Create activity linked to deal and person",
            full = true,
            code = """
                id: create_linked_activity
                namespace: company.team

                tasks:
                  - id: create_activity
                    type: io.kestra.plugin.pipedrive.tasks.ActivitiesCreate
                    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
                    subject: "Follow-up Call"
                    type: "call"
                    dueDate: "2025-01-20"
                    dueTime: "10:00"
                    dealId: 123
                    personId: 456
                    note: "Discuss contract terms and next steps"
                """
        )
    }
)
public class ActivitiesCreate extends Task implements RunnableTask<ActivitiesCreate.Output> {
    
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
        title = "Activity Subject",
        description = "The subject of the activity."
    )
    private Property<String> subject;

    @Schema(
        title = "Activity type (required unless 'type_id' is specified)",
        description = "The name of the activity type. Common types include 'call', 'email', 'meeting', 'task'."
    )
    private Property<String> activityType;    @Schema(
        title = "Due Date",
        description = "Due date of the activity in YYYY-MM-DD format."
    )
    private Property<String> dueDate;

    @Schema(
        title = "Due Time",
        description = "Due time of the activity in HH:MM format (24-hour)."
    )
    private Property<String> dueTime;

    @Schema(
        title = "Duration",
        description = "Duration of the activity in HH:MM format."
    )
    private Property<String> duration;

    @Schema(
        title = "Deal ID",
        description = "The ID of the deal this activity is associated with."
    )
    private Property<Integer> dealId;

    @Schema(
        title = "Person ID",
        description = "The ID of the person this activity is associated with."
    )
    private Property<Integer> personId;

    @Schema(
        title = "Organization ID",
        description = "The ID of the organization this activity is associated with."
    )
    private Property<Integer> organizationId;

    @Schema(
        title = "Note",
        description = "Note about the activity."
    )
    private Property<String> note;

    @Schema(
        title = "Done",
        description = "Whether the activity is done or not. Defaults to false."
    )
    @Builder.Default
    private Property<Boolean> done = Property.ofValue(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String token = runContext.render(apiToken).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("API token is required"));
        String baseUrlValue = runContext.render(baseUrl).as(String.class).orElse("https://api.pipedrive.com/v1");
        String subjectValue = runContext.render(subject).as(String.class).orElseThrow(() ->
            new IllegalArgumentException("Activity subject is required"));

        PipedriveClient client = PipedriveClient.of(runContext, baseUrlValue, token);

        Map<String, Object> activityData = new HashMap<>();
        activityData.put("subject", subjectValue);

        // Add optional fields if provided
        runContext.render(activityType).as(String.class).ifPresent(t -> activityData.put("type", t));
        runContext.render(dueDate).as(String.class).ifPresent(dd -> activityData.put("due_date", dd));
        runContext.render(dueTime).as(String.class).ifPresent(dt -> activityData.put("due_time", dt));
        runContext.render(duration).as(String.class).ifPresent(d -> activityData.put("duration", d));
        runContext.render(dealId).as(Integer.class).ifPresent(did -> activityData.put("deal_id", did));
        runContext.render(personId).as(Integer.class).ifPresent(pid -> activityData.put("person_id", pid));
        runContext.render(organizationId).as(Integer.class).ifPresent(oid -> activityData.put("org_id", oid));
        runContext.render(note).as(String.class).ifPresent(n -> activityData.put("note", n));
        runContext.render(done).as(Boolean.class).ifPresent(d -> activityData.put("done", d ? 1 : 0));

        try {
            HttpResponse<String> response = client.post("/activities", activityData);
            
            runContext.logger().info("Activity created successfully. Response: {}", response.getBody());
            
            // Parse the response to extract activity information
            Map<String, Object> responseData = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);
            Map<String, Object> activityInfo = (Map<String, Object>) responseData.get("data");
            
            Integer activityId = (Integer) activityInfo.get("id");
            
            return ActivitiesCreate.Output.builder()
                .activityId(activityId)
                .subject(subjectValue)
                .response(response.getBody())
                .build();
                
        } catch (HttpClientException e) {
            throw new RuntimeException("Failed to create activity in Pipedrive: " + e.getMessage(), e);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException("Failed to evaluate variables: " + e.getMessage(), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Activity ID",
            description = "The ID of the created activity."
        )
        private final Integer activityId;

        @Schema(
            title = "Subject",
            description = "The subject of the created activity."
        )
        private final String subject;

        @Schema(
            title = "API Response",
            description = "Full response from the Pipedrive API."
        )
        private final String response;
    }
}
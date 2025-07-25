package io.kestra.plugin.core.supabase;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Insert data into a Supabase table using the REST API.",
    description = "This task inserts one or more records into a Supabase table using the REST API."
)
@Plugin(
    examples = {
        @Example(
            title = "Insert a single record.",
            full = true,
            code = """
                id: supabase_insert_single
                namespace: company.team

                tasks:
                  - id: insert_user
                    type: io.kestra.plugin.core.supabase.Insert
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                    data:
                      name: "John Doe"
                      email: "john@example.com"
                      status: "active"
                """
        ),
        @Example(
            title = "Insert multiple records.",
            full = true,
            code = """
                id: supabase_insert_multiple
                namespace: company.team

                tasks:
                  - id: insert_users
                    type: io.kestra.plugin.core.supabase.Insert
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                    data:
                      - name: "John Doe"
                        email: "john@example.com"
                        status: "active"
                      - name: "Jane Smith"
                        email: "jane@example.com"
                        status: "active"
                """
        ),
        @Example(
            title = "Insert with conflict resolution (upsert).",
            full = true,
            code = """
                id: supabase_upsert
                namespace: company.team

                tasks:
                  - id: upsert_user
                    type: io.kestra.plugin.core.supabase.Insert
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                    data:
                      id: 123
                      name: "John Doe Updated"
                      email: "john.updated@example.com"
                    onConflict: "id"
                    resolution: "merge-duplicates"
                """
        )
    }
)
public class Insert extends AbstractSupabase implements RunnableTask<Insert.Output> {

    @Schema(
        title = "The name of the table to insert into.",
        description = "The name of the table in your Supabase database."
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private Property<String> table;

    @Schema(
        title = "The data to insert.",
        description = "The data to insert. Can be a single object or an array of objects."
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private Property<Object> data;

    @Schema(
        title = "Columns to return after insert.",
        description = "Comma-separated list of columns to return after the insert. Defaults to '*' (all columns)."
    )
    @PluginProperty(dynamic = true)
    private Property<String> select;

    @Schema(
        title = "Conflict resolution column(s).",
        description = "Column name(s) to use for conflict resolution (upsert). Comma-separated for multiple columns."
    )
    @PluginProperty(dynamic = true)
    private Property<String> onConflict;

    @Schema(
        title = "Resolution strategy for conflicts.",
        description = "How to handle conflicts: 'merge-duplicates' (default) or 'ignore-duplicates'."
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    private Property<String> resolution = Property.ofValue("merge-duplicates");

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (HttpClient client = this.client(runContext)) {
            String renderedTable = runContext.render(this.table).as(String.class).orElseThrow();
            Object renderedData = runContext.render(this.data).as(Object.class).orElseThrow();
            
            String endpoint = buildTableEndpoint(renderedTable);
            HttpRequest.HttpRequestBuilder requestBuilder = baseRequest(runContext, endpoint)
                .method("POST");

            // Add Prefer header for upsert behavior
            String renderedOnConflict = runContext.render(this.onConflict).as(String.class).orElse(null);
            String renderedResolution = runContext.render(this.resolution).as(String.class).orElse("merge-duplicates");
            
            if (renderedOnConflict != null && !renderedOnConflict.trim().isEmpty()) {
                String preferValue = "resolution=" + renderedResolution;
                requestBuilder.addHeader("Prefer", preferValue);
            }

            // Add return preference
            String renderedSelect = runContext.render(this.select).as(String.class).orElse("*");
            requestBuilder.addHeader("Prefer", "return=representation");
            
            // Add select query parameter
            String baseUri = requestBuilder.build().getUri().toString();
            baseUri += "?select=" + renderedSelect;
            
            // Convert data to JSON
            String jsonBody = JacksonMapper.ofJson().writeValueAsString(renderedData);
            
            HttpRequest request = requestBuilder
                .uri(new URI(baseUri))
                .body(HttpRequest.StringRequestBody.builder()
                    .content(jsonBody)
                    .contentType("application/json")
                    .build())
                .build();

            HttpResponse<Byte[]> response = client.request(request, Byte[].class);

            String responseBody = null;
            if (response.getBody() != null) {
                responseBody = IOUtils.toString(ArrayUtils.toPrimitive(response.getBody()), StandardCharsets.UTF_8.name());
            }

            // Parse response as JSON
            List<Map<String, Object>> insertedRows = null;
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    insertedRows = JacksonMapper.ofJson().readValue(responseBody, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception e) {
                    runContext.logger().warn("Failed to parse response as JSON: {}", e.getMessage());
                    insertedRows = List.of();
                }
            } else {
                insertedRows = List.of();
            }

            return Output.builder()
                .uri(request.getUri())
                .code(response.getStatus().getCode())
                .headers(response.getHeaders().map())
                .insertedRows(insertedRows)
                .insertedCount(insertedRows.size())
                .rawResponse(responseBody)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URI of the executed request."
        )
        private final URI uri;

        @Schema(
            title = "The HTTP status code of the response."
        )
        private final Integer code;

        @Schema(
            title = "The headers of the response."
        )
        @PluginProperty(additionalProperties = List.class)
        private final Map<String, List<String>> headers;

        @Schema(
            title = "The inserted rows returned from the database."
        )
        private final List<Map<String, Object>> insertedRows;

        @Schema(
            title = "The number of rows inserted."
        )
        private final Integer insertedCount;

        @Schema(
            title = "The raw response body."
        )
        private final String rawResponse;
    }
}

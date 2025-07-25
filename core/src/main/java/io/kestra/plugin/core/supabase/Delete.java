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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete data from a Supabase table using the REST API.",
    description = "This task deletes records from a Supabase table using the REST API with support for filtering conditions."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a specific record by ID.",
            full = true,
            code = """
                id: supabase_delete_by_id
                namespace: company.team

                tasks:
                  - id: delete_user
                    type: io.kestra.plugin.core.supabase.Delete
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                    filter: "id=eq.123"
                """
        ),
        @Example(
            title = "Delete multiple records with complex filtering.",
            full = true,
            code = """
                id: supabase_delete_multiple
                namespace: company.team

                tasks:
                  - id: delete_inactive_users
                    type: io.kestra.plugin.core.supabase.Delete
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                    filter: "status=eq.inactive&last_login=lt.2022-01-01"
                """
        ),
        @Example(
            title = "Delete with return of deleted records.",
            full = true,
            code = """
                id: supabase_delete_with_return
                namespace: company.team

                tasks:
                  - id: delete_user_with_audit
                    type: io.kestra.plugin.core.supabase.Delete
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                    filter: "id=eq.123"
                    select: "id,name,email,deleted_at"
                """
        )
    }
)
public class Delete extends AbstractSupabase implements RunnableTask<Delete.Output> {

    @Schema(
        title = "The name of the table to delete from.",
        description = "The name of the table in your Supabase database."
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private Property<String> table;

    @Schema(
        title = "Filter conditions.",
        description = "Filter conditions using PostgREST syntax to specify which records to delete (e.g., 'id=eq.123', 'status=eq.inactive'). Be careful with this filter as it determines which records will be deleted."
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private Property<String> filter;

    @Schema(
        title = "Columns to return after deletion.",
        description = "Comma-separated list of columns to return from the deleted records. Defaults to '*' (all columns). Set to empty string to not return any data."
    )
    @PluginProperty(dynamic = true)
    private Property<String> select;

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (HttpClient client = this.client(runContext)) {
            String renderedTable = runContext.render(this.table).as(String.class).orElseThrow();
            String renderedFilter = runContext.render(this.filter).as(String.class).orElseThrow();
            
            String endpoint = buildTableEndpoint(renderedTable);
            HttpRequest.HttpRequestBuilder requestBuilder = baseRequest(runContext, endpoint)
                .method("DELETE");

            // Add return preference if select is specified
            String renderedSelect = runContext.render(this.select).as(String.class).orElse("*");
            if (renderedSelect != null && !renderedSelect.trim().isEmpty()) {
                requestBuilder.addHeader("Prefer", "return=representation");
            }
            
            // Build query parameters
            List<String> queryParams = new ArrayList<>();
            
            // Select columns to return (if any)
            if (renderedSelect != null && !renderedSelect.trim().isEmpty()) {
                queryParams.add("select=" + renderedSelect);
            }
            
            // Add filter conditions
            if (renderedFilter != null && !renderedFilter.trim().isEmpty()) {
                queryParams.add(renderedFilter);
            }
            
            // Build final URI with query parameters
            String baseUri = requestBuilder.build().getUri().toString();
            if (!queryParams.isEmpty()) {
                String queryString = String.join("&", queryParams);
                baseUri += "?" + queryString;
            }
            
            HttpRequest request = requestBuilder
                .uri(new URI(baseUri))
                .build();

            HttpResponse<Byte[]> response = client.request(request, Byte[].class);

            String responseBody = null;
            if (response.getBody() != null) {
                responseBody = IOUtils.toString(ArrayUtils.toPrimitive(response.getBody()), StandardCharsets.UTF_8.name());
            }

            // Parse response as JSON (if return was requested)
            List<Map<String, Object>> deletedRows = null;
            if (responseBody != null && !responseBody.trim().isEmpty() && 
                renderedSelect != null && !renderedSelect.trim().isEmpty()) {
                try {
                    deletedRows = JacksonMapper.ofJson().readValue(responseBody, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception e) {
                    runContext.logger().warn("Failed to parse response as JSON: {}", e.getMessage());
                    deletedRows = List.of();
                }
            } else {
                deletedRows = List.of();
            }

            // For DELETE operations, the count might be in the Content-Range header
            Integer deletedCount = deletedRows.size();
            List<String> contentRange = response.getHeaders().map().get("content-range");
            if (contentRange != null && !contentRange.isEmpty()) {
                try {
                    String range = contentRange.get(0);
                    // Content-Range format: "0-4/5" means 5 total items
                    if (range.contains("/")) {
                        String total = range.split("/")[1];
                        if (!"*".equals(total)) {
                            deletedCount = Integer.parseInt(total);
                        }
                    }
                } catch (Exception e) {
                    runContext.logger().debug("Could not parse Content-Range header: {}", e.getMessage());
                }
            }

            return Output.builder()
                .uri(request.getUri())
                .code(response.getStatus().getCode())
                .headers(response.getHeaders().map())
                .deletedRows(deletedRows)
                .deletedCount(deletedCount)
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
            title = "The deleted rows returned from the database (if select was specified)."
        )
        private final List<Map<String, Object>> deletedRows;

        @Schema(
            title = "The number of rows deleted."
        )
        private final Integer deletedCount;

        @Schema(
            title = "The raw response body."
        )
        private final String rawResponse;
    }
}

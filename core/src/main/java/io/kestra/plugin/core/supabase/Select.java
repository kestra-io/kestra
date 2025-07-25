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
    title = "Select data from a Supabase table using the REST API.",
    description = "This task queries data from a Supabase table using the REST API with support for filtering, ordering, and pagination."
)
@Plugin(
    examples = {
        @Example(
            title = "Select all records from a table.",
            full = true,
            code = """
                id: supabase_select_all
                namespace: company.team

                tasks:
                  - id: select_users
                    type: io.kestra.plugin.core.supabase.Select
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                """
        ),
        @Example(
            title = "Select specific columns with filtering and ordering.",
            full = true,
            code = """
                id: supabase_select_filtered
                namespace: company.team

                tasks:
                  - id: select_active_users
                    type: io.kestra.plugin.core.supabase.Select
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                    select: "id,name,email,created_at"
                    filter: "status=eq.active"
                    order: "created_at.desc"
                    limit: 50
                """
        ),
        @Example(
            title = "Select with pagination.",
            full = true,
            code = """
                id: supabase_select_paginated
                namespace: company.team

                tasks:
                  - id: select_users_page
                    type: io.kestra.plugin.core.supabase.Select
                    url: https://your-project.supabase.co
                    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
                    table: users
                    limit: 25
                    offset: 50
                """
        )
    }
)
public class Select extends AbstractSupabase implements RunnableTask<Select.Output> {

    @Schema(
        title = "The name of the table to select from.",
        description = "The name of the table in your Supabase database."
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private Property<String> table;

    @Schema(
        title = "Columns to select.",
        description = "Comma-separated list of columns to select. If not specified, all columns (*) will be selected."
    )
    @PluginProperty(dynamic = true)
    private Property<String> select;

    @Schema(
        title = "Filter conditions.",
        description = "Filter conditions using PostgREST syntax (e.g., 'status=eq.active', 'age=gte.18')."
    )
    @PluginProperty(dynamic = true)
    private Property<String> filter;

    @Schema(
        title = "Order by clause.",
        description = "Order by clause using PostgREST syntax (e.g., 'created_at.desc', 'name.asc')."
    )
    @PluginProperty(dynamic = true)
    private Property<String> order;

    @Schema(
        title = "Limit the number of rows returned.",
        description = "Maximum number of rows to return."
    )
    @PluginProperty(dynamic = true)
    private Property<Integer> limit;

    @Schema(
        title = "Offset for pagination.",
        description = "Number of rows to skip for pagination."
    )
    @PluginProperty(dynamic = true)
    private Property<Integer> offset;

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (HttpClient client = this.client(runContext)) {
            String renderedTable = runContext.render(this.table).as(String.class).orElseThrow();
            
            String endpoint = buildTableEndpoint(renderedTable);
            HttpRequest.HttpRequestBuilder requestBuilder = baseRequest(runContext, endpoint)
                .method("GET");

            // Build query parameters
            List<String> queryParams = new ArrayList<>();
            
            // Select columns
            String renderedSelect = runContext.render(this.select).as(String.class).orElse("*");
            queryParams.add("select=" + renderedSelect);
            
            // Filter conditions
            String renderedFilter = runContext.render(this.filter).as(String.class).orElse(null);
            if (renderedFilter != null && !renderedFilter.trim().isEmpty()) {
                queryParams.add(renderedFilter);
            }
            
            // Order by
            String renderedOrder = runContext.render(this.order).as(String.class).orElse(null);
            if (renderedOrder != null && !renderedOrder.trim().isEmpty()) {
                queryParams.add("order=" + renderedOrder);
            }
            
            // Limit
            Integer renderedLimit = runContext.render(this.limit).as(Integer.class).orElse(null);
            if (renderedLimit != null) {
                queryParams.add("limit=" + renderedLimit);
            }
            
            // Offset
            Integer renderedOffset = runContext.render(this.offset).as(Integer.class).orElse(null);
            if (renderedOffset != null) {
                queryParams.add("offset=" + renderedOffset);
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

            // Parse response as JSON
            List<Map<String, Object>> rows = null;
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    rows = JacksonMapper.ofJson().readValue(responseBody, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception e) {
                    runContext.logger().warn("Failed to parse response as JSON: {}", e.getMessage());
                    rows = List.of();
                }
            } else {
                rows = List.of();
            }

            return Output.builder()
                .uri(request.getUri())
                .code(response.getStatus().getCode())
                .headers(response.getHeaders().map())
                .rows(rows)
                .size(rows.size())
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
            title = "The selected rows from the table."
        )
        private final List<Map<String, Object>> rows;

        @Schema(
            title = "The number of rows returned."
        )
        private final Integer size;

        @Schema(
            title = "The raw response body."
        )
        private final String rawResponse;
    }
}

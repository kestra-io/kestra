package io.kestra.plugin.core.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.TruthUtils;
import io.micronaut.core.util.functional.ThrowingFunction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Schema(
    title = "Filter a file by retaining only the items that match a given expression."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                """
                tasks:
                   - id: filter
                     type: io.kestra.plugin.core.storage.FilterItems
                     from: "{{ inputs.file }}"
                     filterCondition: " {{ value == null }}"
                     filterType: EXCLUDE
                     errorOrNullBehavior: EXCLUDE
                """
            }
        )
    },
    aliases = "io.kestra.core.tasks.storages.FilterItems"
)
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class FilterItems extends Task implements RunnableTask<FilterItems.Output> {

    @Schema(
        title = "The file to be filtered.",
        description = "Must be a `kestra://` internal storage URI."
    )
    @NotNull
    private Property<String> from;

    @Schema(
        title = "The 'pebble' expression used to match items to be included or excluded.",
        description = "The 'pebble' expression should return a BOOLEAN value (i.e. `true` or `false`). Values `0`, `-0`, and `\"\"` are interpreted as `false`. " +
            "Otherwise, any non empty value will be interpreted as `true`."
    )
    @PluginProperty
    @NotNull
    private String filterCondition;

    @Schema(
        title = "Specifies the action to perform with items that match the `filterCondition` predicate",
        description = "Use `INCLUDE` to pass the item through, or `EXCLUDE` to drop the items."
    )
    @Builder.Default
    private Property<FilterType> filterType = Property.of(FilterType.INCLUDE);

    @Schema(
        title = "Specifies the behavior when the expression fail to be evaluated on an item or return `null`.",
        description = "Use `FAIL` to throw the exception and fail the task, `INCLUDE` to pass the item through, or `EXCLUDE` to drop the item."
    )
    @Builder.Default
    private Property<ErrorOrNullBehavior> errorOrNullBehavior = Property.of(ErrorOrNullBehavior.FAIL);

    /**
     * {@inheritDoc}
     **/
    @Override
    public Output run(RunContext runContext) throws Exception {

        URI from = new URI(runContext.render(this.from).as(String.class).orElseThrow());

        final PebbleExpressionPredicate predicate = getExpressionPredication(runContext);

        final Path path = runContext.workingDir().createTempFile(".ion");
        long processedItemsTotal = 0L;
        long droppedItemsTotal = 0L;
        try (final BufferedWriter writer = Files.newBufferedWriter(path);
             final BufferedReader reader = newBufferedReader(runContext, from)) {

            String item;
            while ((item = reader.readLine()) != null) {
                IllegalVariableEvaluationException exception = null;
                Boolean match = null;
                try {
                    match = predicate.apply(item);
                } catch (IllegalVariableEvaluationException e) {
                    exception = e;
                }

                FilterType action = runContext.render(this.filterType).as(FilterType.class).orElseThrow();

                if (match == null) {
                    switch (runContext.render(errorOrNullBehavior).as(ErrorOrNullBehavior.class).orElseThrow()) {
                        case FAIL -> {
                            if (exception != null) {
                                throw exception;
                            } else {
                                throw new IllegalVariableEvaluationException(String.format(
                                    "Expression `%s` return `null` on item `%s`",
                                    filterCondition,
                                    item
                                ));
                            }
                        }
                        case INCLUDE -> action = FilterType.INCLUDE;
                        case EXCLUDE ->  action = FilterType.EXCLUDE;
                    }
                    match = true;
                }

                if (!match) {
                    action = action.reverse();
                }

                switch (action) {
                    case INCLUDE -> {
                        writer.write(item);
                        writer.newLine();
                    }
                    case EXCLUDE -> droppedItemsTotal++;
                }
                processedItemsTotal++;
            }
        }
        URI uri = runContext.storage().putFile(path.toFile());
        return Output.builder()
            .uri(uri)
            .processedItemsTotal(processedItemsTotal)
            .droppedItemsTotal(droppedItemsTotal)
            .build();
    }

    private PebbleExpressionPredicate getExpressionPredication(RunContext runContext) {
        return new PebbleExpressionPredicate(runContext, filterCondition);
    }

    private BufferedReader newBufferedReader(final RunContext runContext, final URI objectURI) throws IOException {
        InputStream is = runContext.storage().getFile(objectURI);
        return new BufferedReader(new InputStreamReader(is));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The filtered file URI."
        )
        private final URI uri;

        @Schema(
            title = "The total number of items that was processed by the task."
        )
        private final Long processedItemsTotal;

        @Schema(
            title = "The total number of items that was dropped by the task."
        )
        private final Long droppedItemsTotal;
    }

    private static class PebbleExpressionPredicate implements ThrowingFunction<String, Boolean, Exception> {

        protected static final ObjectMapper MAPPER = JacksonMapper.ofIon();
        private final RunContext runContext;
        private final String expression;

        /** {@inheritDoc} */
        @Override
        public Boolean apply(String data) throws Exception {
            try {
                String rendered = extract(MAPPER.readTree(data));
                return rendered == null ? null : TruthUtils.isTruthy(rendered.trim());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Creates a new {@link PebbleExpressionPredicate} instance.
         *
         * @param expression the 'pebble' expression.
         */
        public PebbleExpressionPredicate(final RunContext runContext,
                                         final String expression) {
            this.runContext = runContext;
            this.expression = expression;
        }

        public String extract(final JsonNode jsonNode) throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.convertValue(jsonNode, Map.class);
            return runContext.render(expression, map);
        }
    }

    public enum FilterType {
        INCLUDE, EXCLUDE;

        public FilterType reverse() {
            return equals(INCLUDE) ? EXCLUDE : INCLUDE;
        }
    }

    public enum ErrorOrNullBehavior {
        FAIL, INCLUDE, EXCLUDE;
    }
}

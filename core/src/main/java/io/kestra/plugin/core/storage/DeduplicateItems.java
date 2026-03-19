package io.kestra.plugin.core.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
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
import java.util.HashMap;
import java.util.Map;

@Schema(
    title = "Deduplicate a line-oriented file by key.",
    description = """
        Reads the file twice: first to map each key (from `expr`) to its last occurrence offset, then to write only those last occurrences to a new file. Avoids loading the full file in memory.

        Use for ordered “keep-last” semantics; expression can reference columns directly."""
)
@Plugin(
    examples = {
        @Example(
            title = "Remove duplicate customer emails from a CSV file.",
            full = true,
            code = """
                id: deduplicate_items
                namespace: company.team

                tasks:
                  - id: generate_files
                    type: io.kestra.plugin.scripts.shell.Script
                    script: |
                      cat <<EOF > my_data.csv
                      order_id,customer_name,customer_email,product_id,price
                      1,Kelly Olsen,kelly@example.com,20,166.89
                      2,Miguel Moore,mccarthylee@example.net,14,171.63
                      3,Kelly Olsen,kelly@example.com,20,166.89
                      4,Jessica White,jessica@example.com,12,50.62
                      5,Jessica White,jessica@example.com,12,50.62
                      EOF
                    outputFiles:
                      - "my_data.csv"
                    
                  - id: csv_to_ion
                    type: io.kestra.plugin.serdes.csv.CsvToIon
                    from: "{{ outputs.generate_files.outputFiles['my_data.csv'] }}"

                  - id: dedup
                    type: io.kestra.plugin.core.storage.DeduplicateItems
                    from: "{{ outputs.csv_to_ion.uri }}"
                    expr: "{{ customer_email }}"
            """
        )
    },
    aliases = "io.kestra.core.tasks.storages.DeduplicateItems"
)
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class DeduplicateItems extends Task implements RunnableTask<DeduplicateItems.Output> {

    @Schema(
        title = "The file to be deduplicated"
    )
    @NotNull
    @PluginProperty(internalStorageURI = true)
    private Property<String> from;

    @Schema(
        title = "The Pebble expression to extract the deduplication key from each item",
        description = "Headers from the file can be referenced directly e.g. `{{ customer_email }}`"
    )
    @PluginProperty
    @NotNull
    private String expr;

    /**
     * {@inheritDoc}
     **/
    @Override
    public Output run(RunContext runContext) throws Exception {

        URI from = new URI(runContext.render(this.from).as(String.class).orElseThrow());

        final PebbleFieldExtractor keyExtractor = getKeyExtractor(runContext);

        final Map<String, Long> index = new HashMap<>(); // can be replaced by small-footprint Map implementation

        // 1st iteration: build a map of key->offset
        try (final BufferedReader reader = newBufferedReader(runContext, from)) {
            long offset = 0L;
            String item;
            while ((item = reader.readLine()) != null) {
                String key = keyExtractor.apply(item);
                index.put(key, offset);
                offset++;
            }
        }

        // metrics
        long processedItemsTotal = 0L;
        long droppedItemsTotal = 0L;
        long numKeys = index.size();

        final Path path = runContext.workingDir().createTempFile(".ion");
        // 2nd iteration: write deduplicate
        try (final BufferedWriter writer = Files.newBufferedWriter(path);
             final BufferedReader reader = newBufferedReader(runContext, from)) {
            long offset = 0L;
            String item;
            while ((item = reader.readLine()) != null) {
                String key = keyExtractor.apply(item);
                Long lastOffset = index.get(key);
                if (lastOffset != null && lastOffset == offset) {
                    writer.write(item);
                    writer.newLine();
                } else {
                    droppedItemsTotal++;
                }
                offset++;
                processedItemsTotal++;
            }
        }
        URI uri = runContext.storage().putFile(path.toFile());
        index.clear();
        return Output
            .builder()
            .uri(uri)
            .numKeys(numKeys)
            .processedItemsTotal(processedItemsTotal)
            .droppedItemsTotal(droppedItemsTotal)
            .build();
    }

    private PebbleFieldExtractor getKeyExtractor(RunContext runContext) {
        return new PebbleFieldExtractor(runContext, expr);
    }

    private BufferedReader newBufferedReader(final RunContext runContext, final URI objectURI) throws IOException {
        InputStream is = runContext.storage().getFile(objectURI);
        return new BufferedReader(new InputStreamReader(is));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The deduplicated file URI"
        )
        private final URI uri;

        @Schema(
            title = "The number of distinct keys observed by the task"
        )
        private final Long numKeys;

        @Schema(
            title = "The total number of items that was processed by the task"
        )
        private final Long processedItemsTotal;

        @Schema(
            title = "The total number of items that was dropped by the task"
        )
        private final Long droppedItemsTotal;
    }

    /**
     * Extracts a key from data using a 'pebble' expression.
     */
    private static class PebbleFieldExtractor implements ThrowingFunction<String, String, Exception> {

        protected static final ObjectMapper MAPPER = JacksonMapper.ofIon();
        private final RunContext runContext;
        private final String expression;

        /**
         * Creates a new {@link PebbleFieldExtractor} instance.
         *
         * @param expression the 'pebble' expression.
         */
        public PebbleFieldExtractor(final RunContext runContext,
                                    final String expression) {
            this.runContext = runContext;
            this.expression = expression;
        }


        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public String apply(String data) throws Exception {
            try {
                return extract(MAPPER.readValue(data, Map.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public String extract(final Map<String, Object> item) throws Exception {
            return runContext.render(expression, item);
        }
    }
}

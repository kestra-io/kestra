package io.kestra.runner.kafka.streams;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.runners.Executor;
import io.kestra.core.serializers.JacksonMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;

import java.nio.charset.StandardCharsets;

@Slf4j
public class ExecutorAddHeaderTransformer implements ValueTransformerWithKey<String, Executor, Executor> {
    private ProcessorContext context;

    public ExecutorAddHeaderTransformer() {
    }

    @Override
    public void init(final ProcessorContext context) {
        this.context = context;
    }

    @Override
    public Executor transform(final String key, final Executor value) {
        try {
            this.context.headers().add(
                "from",
                JacksonMapper.ofJson().writeValueAsString(value.getFrom()).getBytes(StandardCharsets.UTF_8)
            );

            this.context.headers().add(
                "offset",
                JacksonMapper.ofJson().writeValueAsString(value.getOffset()).getBytes(StandardCharsets.UTF_8)
            );
        } catch (JsonProcessingException e) {
            log.warn("Unable to add headers", e);
        }

        return value;
    }

    @Override
    public void close() {
    }
}

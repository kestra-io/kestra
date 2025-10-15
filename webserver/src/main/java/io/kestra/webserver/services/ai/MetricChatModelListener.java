package io.kestra.webserver.services.ai;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class MetricChatModelListener implements ChatModelListener {
    @Inject
    private MeterRegistry meterRegistry;

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatRequest request = responseContext.chatRequest();
        ChatResponse response = responseContext.chatResponse();

        String[] tags = new String[]{
            "messages_count", String.valueOf(request.messages().size()),
            "model", request.modelName(),
            "finish_reasons", response.finishReason().toString()
        };

        TokenUsage tokenUsage = response.tokenUsage();
        if (tokenUsage != null) {
            if (tokenUsage.outputTokenCount() != null) {
                meterRegistry
                    .counter("ai.copilot.input_token", tags)
                    .increment(tokenUsage.inputTokenCount());
            }

            if (tokenUsage.outputTokenCount() != null) {
                meterRegistry
                    .counter("ai.copilot.output_token", tags)
                    .increment(tokenUsage.outputTokenCount());
            }

            if (tokenUsage.totalTokenCount() != null) {
                meterRegistry
                    .counter("ai.copilot.total_token", tags)
                    .increment(tokenUsage.totalTokenCount());
            }
        }

        this.recordDuration(responseContext.attributes(), tags);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        ChatRequest request = errorContext.chatRequest();

        String[] tags = new String[]{
            "messages_count", String.valueOf(request.messages().size()),
            "model", request.modelName(),
            "finish_reasons", "ERROR",
            "error_type", errorContext.error().getClass().getName()
        };

        this.recordDuration(errorContext.attributes(), tags);
    }

    private void recordDuration(Map<Object, Object> attributes, String[] tags) {
        Long startTime = (Long) attributes.get(MetadataAppenderChatModelListener.START_TIME_KEY_NAME);
        if (startTime == null) {
            // should never happen
            log.warn("No start time found in response");
            return;
        }

        final long endTime = Clock.SYSTEM.monotonicTime();

        meterRegistry
            .timer("ai.copilot.request.duration", tags)
            .record(endTime - startTime, TimeUnit.NANOSECONDS);
    }
}

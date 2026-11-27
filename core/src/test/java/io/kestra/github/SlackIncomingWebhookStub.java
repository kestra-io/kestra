package io.kestra.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Stands in for the Slack task of the notifications plugin, which the core tests do not have: same type, same
 * {@code url} and {@code messageText}, same empty output.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin(aliases = "io.kestra.plugin.notifications.slack.SlackIncomingWebhook")
public class SlackIncomingWebhookStub extends Task implements RunnableTask<VoidOutput> {
    private String url;

    private Property<String> messageText;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String payload = JacksonMapper.ofJson().writeValueAsString(
            Map.of("text", runContext.render(messageText).as(String.class).orElseThrow())
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(runContext.render(url)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Slack answered with the status %d.".formatted(response.statusCode()));
        }
        return null;
    }
}

package io.kestra.webserver.controllers.api;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.plugin.core.trigger.WebhookContext;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class WebhookPluginTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows(value = {"flows/valids/webhook-plugin.yaml"})
    void pluginWorks() throws InterruptedException {
        CountDownLatch queueCount = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("webhook-plugin") && execution.getLeft().getTrigger() != null && execution.getLeft().getTrigger().getId().equals("webhook1")) {
                queueCount.countDown();
            }
        });

        var response = client.toBlocking().exchange(
            PUT(
                "/api/v1/main/executions/webhook/io.kestra.tests/webhook-plugin/case1",
                "{\"test\": \"data\"}"
            ),
            String.class
        );

        assertThat((Object)response.getStatus()).isEqualTo(HttpStatus.OK);

        queueCount.await(10, TimeUnit.SECONDS);
        assertThat(((Map<String, String>)Objects.requireNonNull(receive.blockLast()).getTrigger().getVariables().get("body")).get("test")).isEqualTo("data");
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-plugin.yaml"})
    void webbookFailedExecution() throws InterruptedException {
        CountDownLatch queueCount = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("webhook-plugin") && execution.getLeft().getTrigger() != null && execution.getLeft().getTrigger().getId().equals("webhook2")) {
                queueCount.countDown();
            }
        });

        // Test that wrong namespace returns 404
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(
                    "/api/v1/main/executions/webhook/io.kestra.tests/webhook-plugin/case2/failed",
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        queueCount.await(10, TimeUnit.SECONDS);
        assertThat(Objects.requireNonNull(receive.blockLast()).getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode(callSuper = true)
    @Getter
    @NoArgsConstructor
    @Plugin
    public static class WebhookTestPlugin extends AbstractWebhookTrigger implements TriggerOutput<VoidOutput> {
        @Builder.Default
        private Boolean failed = false;

        @Override
        public HttpResponse<?> evaluate(WebhookContext context) throws Exception {
            if (context.getPath() != null && context.getPath().equals("failed")) {
                throw new Exception("Failed as requested");
            }

            Optional<Execution> maybeExecution = context.getWebhookService().newExecution(
                context,
                context.getFlow(),
                this,
                Webhook.Output.builder()
                    .body(JacksonMapper.toMap((String) context.getRequest().getBody().getContent()))
                    .headers(context.getRequest().getHeaders().map())
                    .parameters(context.getWebhookService().parseParameters(context))
                    .build()
            );

            if (maybeExecution.isEmpty()) {
                return HttpResponse.of(HttpResponse.Status.CONFLICT);
            }

            Execution execution = maybeExecution.get();

            try {
                context.getWebhookService().startExecution(execution);
            } catch (QueueException e) {
                return HttpResponse.of(HttpResponse.Status.INTERNAL_SERVER_ERROR);
            }

            return HttpResponse.of(HttpStatus.OK);
        }
    }
}

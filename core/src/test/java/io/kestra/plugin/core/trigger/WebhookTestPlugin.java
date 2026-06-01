package io.kestra.plugin.core.trigger;

import java.util.Optional;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.http.HttpStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Plugin
public class WebhookTestPlugin extends AbstractWebhookTrigger implements TriggerOutput<WebhookTestPlugin.WebhookTestOutput> {
    @Builder.Default
    private Boolean failed = false;

    @Override
    public Mono<HttpResponse<?>> evaluate(WebhookContext context) throws Exception {
        if (context.path() != null && context.path().equals("failed")) {
            throw new Exception("Failed as requested");
        }

        Optional<Execution> maybeExecution = context.webhookService().newExecution(
            context,
            context.flow(),
            this,
            WebhookTestOutput.builder()
                .body(JacksonMapper.toMap((String) context.request().getBody().getContent()))
                .encryptedString(EncryptedString.from("super-secret", context.webhookService().runContext(context.flow(), context.trigger())))
                .build()
        );

        if (maybeExecution.isEmpty()) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.CONFLICT));
        }

        Execution execution = maybeExecution.get();

        return context.webhookService().startExecution(execution)
            .<HttpResponse<?>>thenReturn(HttpResponse.of(HttpStatus.OK))
            .onErrorReturn(HttpResponse.of(HttpResponse.Status.INTERNAL_SERVER_ERROR));
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookTestOutput implements io.kestra.core.models.tasks.Output {
        private Object body;
        private EncryptedString encryptedString;
    }
}
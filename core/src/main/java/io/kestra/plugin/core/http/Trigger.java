package io.kestra.plugin.core.http;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.SslOptions;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TruthUtils;
import io.micronaut.http.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow based on an HTTP response"
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Slack alert if the price is below a certain threshold. The flow will be triggered every 30 seconds until the condition is met. Then, the `stopAfter` property will disable the trigger to avoid unnecessary API calls and alerts.",
            full = true,
            code = """
                id: http_price_alert
                namespace: company.team

                tasks:
                  - id: send_slack_alert
                    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                    url: "{{ secret('SLACK_WEBHOOK') }}"
                    payload: |
                      {
                        "channel": "#price-alerts",
                        "text": "The price is now: {{ json(trigger.body).price }}"
                      }

                triggers:
                  - id: http
                    type: io.kestra.plugin.core.http.Trigger
                    uri: https://fakestoreapi.com/products/1
                    responseCondition: "{{ json(response.body).price <= 110 }}"
                    interval: PT30S
                    stopAfter:
                      - SUCCESS
                """
        ),
        @Example(
            title = "Trigger a flow if an HTTP endpoint returns a status code equals to 200",
            full = true,
            code = """
                id: http_trigger
                namespace: company.team

                tasks:
                  - id: log_response
                    type: io.kestra.plugin.core.log.Log
                    message: '{{ trigger.body }}'

                triggers:
                  - id: http
                    type: io.kestra.plugin.core.http.Trigger
                    uri: https://api.chucknorris.io/jokes/random
                    responseCondition: "{{ response.statusCode == 200 }}"
                    stopAfter:
                      - SUCCESS
                """
        )
    },
    aliases = "io.kestra.plugin.fs.http.Trigger"
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, HttpInterface, TriggerOutput<Request.Output> {
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(
            title = "The condition on the HTTP response to trigger a flow which can be any expression that evaluates to a boolean value.",
            description = """
                The condition will be evaluated after calling the HTTP endpoint, it can use the response itself to determine whether to start a flow or not.
                The following variables are available when evaluating the condition:
                - `response.statusCode`: the response HTTP status code
                - `response.body`: the response body as a string
                - `response.headers`: the response headers

                Boolean coercion allows 0, -0, null and '' to evaluate to false, all other values will evaluate to true.

                The condition will be evaluated before any 'generic trigger conditions' that can be configured via the `conditions` property.
                """
    )
    @Builder.Default
    @NotNull
    private Property<String> responseCondition = new Property<>("{{ response.statusCode < 400 }}");

    @NotNull
    private Property<String> uri;

    @Builder.Default
    private Property<String> method = Property.of("GET");

    private Property<String> body;

    private Property<Map<String, Object>> formData;

    @Builder.Default
    private Property<String> contentType = Property.of(MediaType.APPLICATION_JSON);

    private Property<Map<CharSequence, CharSequence>> headers;

    private HttpConfiguration options;

    @Builder.Default
    @Schema(
        title = "If true, the HTTP response body will be automatically encrypted and decrypted in the outputs if encryption is configured",
        description = "When true, the `encryptedBody` output will be filled, otherwise the `body` output will be filled"
    )
    private Property<Boolean> encryptBody = Property.of(false);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Logger logger = runContext.logger();

        var request = Request.builder()
            .uri(this.uri)
            .method(this.method)
            .body(this.body)
            .formData(this.formData)
            .contentType(this.contentType)
            .headers(this.headers)
            .options((this.options == null ? HttpConfiguration.builder() : this.options.toBuilder())
                // we allow failed status code as it is the condition that must determine whether we trigger the flow
                .allowFailed(Property.of(true))
                .ssl(this.options != null && this.options.getSsl() != null ? this.options.getSsl() : this.sslOptions)
                .build()
            )
            .encryptBody(this.encryptBody)
            .build();
        var output = request.run(runContext);

        logger.debug("{} respond with status code '{}'", output.getUri(), output.getCode());

        Object body = runContext.render(this.encryptBody).as(Boolean.class).orElseThrow()
            ? runContext.decrypt(output.getEncryptedBody().getValue())
            : output.getBody();

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", output.getCode());
        response.put("body", body); // body can be null so we need a null-friendly map
        response.put("headers", output.getHeaders());
        Map<String, Object> responseVariables = Map.of("response", response);
        String renderedCondition = runContext.render(this.responseCondition).as(String.class, responseVariables).orElse(null);
        if (TruthUtils.isTruthy(renderedCondition)) {
            Execution execution = TriggerService.generateExecution(this, conditionContext, context, output);

            return Optional.of(execution);
        }

        return Optional.empty();
    }

    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private SslOptions sslOptions;

    @Deprecated
    public void sslOptions(SslOptions sslOptions) {
        if (this.options == null) {
            this.options = HttpConfiguration.builder()
                .build();
        }

        this.sslOptions = sslOptions;
        this.options = this.options.toBuilder()
            .ssl(sslOptions)
            .build();
    }
}

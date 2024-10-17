package io.kestra.plugin.core.http;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TruthUtils;
import io.micronaut.http.HttpMethod;
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
    @PluginProperty(dynamic = true)
    @Builder.Default
    @NotNull
    private String responseCondition = "{{ response.statusCode < 400 }}";

    @NotNull
    private String uri;

    @Builder.Default
    private HttpMethod method = HttpMethod.GET;

    private String body;

    private Map<String, Object> formData;

    private String contentType;

    private Map<CharSequence, CharSequence> headers;

    private RequestOptions options;

    private SslOptions sslOptions;

    @Builder.Default
    @Schema(
        title = "If true, the HTTP response body will be automatically encrypted and decrypted in the outputs if encryption is configured",
        description = "When true, the `encryptedBody` output will be filled, otherwise the `body` output will be filled"
    )
    private boolean encryptBody = false;


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
            .options(this.options)
            .sslOptions(this.sslOptions)
            // we allow failed status code as it is the condition that must determine whether we trigger the flow
            .allowFailed(true)
            .encryptBody(this.encryptBody)
            .build();
        var output = request.run(runContext);

        logger.debug("{} respond with status code '{}'", output.getUri(), output.getCode());

        Object body = this.encryptBody
            ? runContext.decrypt(output.getEncryptedBody().getValue())
            : output.getBody();
        Map<String, Object> responseVariables = Map.of("response", new HashMap<>(){
                {put("statusCode", output.getCode());}
                {put("body", body);}
                {put("headers", output.getHeaders());}
            }
        );
        var renderedCondition = runContext.render(this.responseCondition, responseVariables);
        if (TruthUtils.isTruthy(renderedCondition)) {
            Execution execution = TriggerService.generateExecution(this, conditionContext, context, output);

            return Optional.of(execution);
        }

        return Optional.empty();
    }
}

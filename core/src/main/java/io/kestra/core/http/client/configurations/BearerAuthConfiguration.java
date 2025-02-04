package io.kestra.core.http.client.configurations;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;

@Getter
@SuperBuilder(toBuilder = true)
public class BearerAuthConfiguration extends AbstractAuthConfiguration {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected Property<AuthType> type = Property.of(AuthType.BEARER);

    @Schema(title = "The token for bearer token authentication.")
    private final Property<String> token;

    @Override
    public void configure(HttpClientBuilder builder, RunContext runContext) throws IllegalVariableEvaluationException {
        var renderedToken = runContext.render(this.token).as(String.class).orElse(null);
        builder.addRequestInterceptorFirst((request, entity, context) -> request
            .setHeader(new BasicHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + renderedToken
            )));
    }
}

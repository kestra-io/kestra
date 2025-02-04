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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Getter
@SuperBuilder(toBuilder = true)
public class BasicAuthConfiguration extends AbstractAuthConfiguration {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected Property<AuthType> type = Property.of(AuthType.BASIC);

    @Schema(title = "The username for HTTP basic authentication.")
    private final Property<String> username;

    @Schema(title = "The password for HTTP basic authentication.")
    private final Property<String> password;

    @Override
    public void configure(HttpClientBuilder builder, RunContext runContext) throws IllegalVariableEvaluationException {
        byte[] encoded = Base64.getEncoder()
            .encode((runContext.render(this.getUsername()).as(String.class).orElse(null)
                + ":"
                + runContext.render(this.getPassword()).as(String.class).orElse(null)
            ).getBytes(StandardCharsets.UTF_8));

        builder.addRequestInterceptorFirst((request, entity, context) -> request
            .setHeader(new BasicHeader(
                HttpHeaders.AUTHORIZATION,
                "Basic " + new String(encoded, StandardCharsets.UTF_8)
            )));
    }
}

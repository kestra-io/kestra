package io.kestra.core.http.client.configurations;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public class DigestAuthConfiguration extends AbstractAuthConfiguration {
    @NotNull
    @JsonInclude
    @Builder.Default
    @Getter(AccessLevel.NONE)
    protected AuthType type = AuthType.DIGEST;

    @Schema(title = "The username for HTTP Digest authentication.")
    private Property<String> username;

    @Schema(title = "The password for HTTP Digest authentication.")
    private Property<String> password;

    @Override
    public void configure(HttpClientBuilder builder, RunContext runContext) throws IllegalVariableEvaluationException {
        builder.setTargetAuthenticationStrategy(new DefaultAuthenticationStrategy());
    }

    @Override
    public AuthType getType() {
        return this.type;
    }
}

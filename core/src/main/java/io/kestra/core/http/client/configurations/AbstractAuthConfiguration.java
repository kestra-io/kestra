package io.kestra.core.http.client.configurations;

import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = BasicAuthConfiguration.class, name = "BASIC"),
        @JsonSubTypes.Type(value = BearerAuthConfiguration.class, name = "BEARER"),
        @JsonSubTypes.Type(value = DigestAuthConfiguration.class, name = "DIGEST")
    }
)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public abstract class AbstractAuthConfiguration {
    public abstract AuthType getType();

    public abstract void configure(HttpClientBuilder builder, RunContext runContext) throws IllegalVariableEvaluationException;

    /**
     * Configures the client used by the {@code HTTP_2} and {@code NEGOTIATE} versions.
     * Not abstract so that existing subclasses keep compiling, but they are rejected with these versions until they override it.
     */
    public void configure(HttpAsyncClientBuilder builder, RunContext runContext) throws IllegalVariableEvaluationException {
        throw new UnsupportedOperationException("The authentication type '%s' is not supported with HTTP/2.".formatted(getType()));
    }

    public enum AuthType {
        BASIC,
        BEARER,
        DIGEST
    }
}

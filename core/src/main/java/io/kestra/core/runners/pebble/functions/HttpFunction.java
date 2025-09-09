package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientRequestException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.DefaultMessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.http.uri.UriBuilder;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class HttpFunction<T> implements Function {
    public static final String NAME = "http";

    private final MessageBodyWriter<T> FALLBACK_CONTENT_WRITER = (type, mediaType, object, outgoingHeaders, outputStream) -> {
        if (mediaType == MediaType.APPLICATION_YAML_TYPE || mediaType.equals(MediaType.of("application/yaml"))) {
            try {
                outputStream.write(JacksonMapper.ofYaml().writeValueAsString(object).getBytes(StandardCharsets.UTF_8));
                return;
            } catch (IOException e) {
                throw new PebbleException(e, "Couldn't write the request body as YAML");
            }
        }

        throw new PebbleException(new IllegalArgumentException("Unsupported content type: " + mediaType), "Unsupported content type ");
    };

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private DefaultMessageBodyHandlerRegistry defaultMessageBodyHandlerRegistry;

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        throwIfMissingArgs(args, self, lineNumber);

        EvaluationContextImpl evaluationContext = (EvaluationContextImpl) context;
        Map<String, Object> pebbleVariables = evaluationContext.getScopeChain().getGlobalScopes().stream()
            .flatMap(scope -> scope.getKeys().stream())
            .distinct()
            .collect(HashMap::new, (m, k) -> m.put(k, context.getVariable(k)), HashMap::putAll);

        // We need late injection otherwise there is a circular dependency issue between Extension and VariableRenderer from RunContextFactory
        RunContextFactory runContextFactory = applicationContext.getBean(RunContextFactory.class);
        RunContext runContext = runContextFactory.of(pebbleVariables);

        URI uri = args.get("uri") instanceof URI uriObject
            ? uriObject
            : URI.create(args.get("uri").toString());
        Map<String, Object> query = (Map<String, Object>) args.getOrDefault("query", Collections.emptyMap());
        String method = args.getOrDefault("method", "GET").toString().toUpperCase();
        String contentType = args.getOrDefault("contentType", MediaType.APPLICATION_JSON).toString();
        String accept = args.getOrDefault("accept", MediaType.APPLICATION_JSON).toString();

        HttpRequest.RequestBody body = null;
        Map<String, List<String>> headers = Optional.ofNullable((Map<String, Object>) args.get("headers"))
            .map(this::singleValueToListForHeaders)
            .orElse(new HashMap<>());
        // Content-Type is already supplied through the RequestBody, so we remove it from headers
        headers.remove("Content-Type");
        headers.put("Accept", List.of(accept));

        if (args.containsKey("body")) {
            body = toRequestBody(args, self, lineNumber, contentType);
        }

        UriBuilder uriWithQueryBuilder = UriBuilder.of(uri);
        query.forEach(uriWithQueryBuilder::queryParam);

        HttpRequest httpRequest = HttpRequest.of(uriWithQueryBuilder.build(), method, body, headers);
        HttpConfiguration httpConfiguration = Optional.ofNullable(args.get("options"))
            .map(o -> JacksonMapper.toMap(o, HttpConfiguration.class))
            .orElse(null);

        try (HttpClient httpClient = new HttpClient(runContext, httpConfiguration)) {
            HttpResponse<Object> response = httpClient.request(httpRequest, Object.class);
            return response.getBody();
        } catch (HttpClientResponseException e) {
            if (e.getResponse() != null) {
                String msg = "Failed to execute HTTP Request, server respond with status " + e.getResponse().getStatus().getCode() + " : " + e.getResponse().getStatus().getReason();
                throw new PebbleException(e, msg , lineNumber, self.getName());
            } else {
                throw new PebbleException( e, "Failed to execute HTTP request ", lineNumber, self.getName());
            }
        } catch(HttpClientException | IllegalVariableEvaluationException | IOException e ) {
            throw new PebbleException( e, "Failed to execute HTTP request ", lineNumber, self.getName());
        }
    }

    private Map<String, List<String>> singleValueToListForHeaders(Map<String, Object> m) {
        return m.entrySet().stream()
            .map(e -> Map.entry(e.getKey(), e.getValue() instanceof String valueStr ? List.of(valueStr) : (List<String>) e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private HttpRequest.RequestBody toRequestBody(Map<String, Object> args, PebbleTemplate self, int lineNumber, String contentType) {
        HttpRequest.RequestBody body;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Class<T> bodyClass = (Class<T>) args.get("body").getClass();
        MessageBodyWriter<T> bodyWriter = defaultMessageBodyHandlerRegistry.findWriter(Argument.of(bodyClass), MediaType.of(contentType)).orElse(FALLBACK_CONTENT_WRITER);
        bodyWriter.writeTo(Argument.of(bodyClass), MediaType.of(contentType), (T) args.get("body"), new SimpleHttpHeaders(), byteArrayOutputStream);
        try {
            body = HttpRequest.RequestBody.from(
                new ByteArrayEntity(byteArrayOutputStream.toByteArray(), ContentType.create(contentType))
            );
        } catch (IOException e) {
            throw new PebbleException(e, "Couldn't parse the request body", lineNumber, self.getName());
        }
        return body;
    }

    private void throwIfMissingArgs(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        if (!args.containsKey("uri")) {
            throw new PebbleException(null, "The 'http' function expects an argument 'uri'.", lineNumber, self.getName());
        }
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("uri", "method", "query", "body", "contentType", "headers", "options", "accept");
    }
}

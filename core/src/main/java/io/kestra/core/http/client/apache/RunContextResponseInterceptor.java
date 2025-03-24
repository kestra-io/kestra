package io.kestra.core.http.client.apache;

import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.runners.RunContext;
import lombok.AllArgsConstructor;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;

@AllArgsConstructor
public class RunContextResponseInterceptor implements HttpResponseInterceptor {
    RunContext runContext;

    @Override
    public void process(HttpResponse response, EntityDetails entity, HttpContext context) throws HttpException, IOException {
        if (context instanceof HttpClientContext httpClientContext &&
            response instanceof BasicClassicHttpResponse httpResponse
        ) {
            try {
                runContext.logger().debug(
                    "Request '{}' from '{}' with the response code '{}'",
                    httpClientContext.getRequest().getUri(),
                    httpClientContext.getEndpointDetails().getRemoteAddress(),
                    response.getCode()
                );
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }

            String[] tags = this.tags(httpClientContext.getRequest(), httpResponse);

            if (httpResponse.getEntity() != null) {
                runContext.metric(Counter.of(
                    "response.length", httpResponse.getEntity().getContentLength(),
                    tags
                ));
            }

            runContext.metric(Counter.of(
                "request.count",
                httpClientContext.getEndpointDetails().getRequestCount(),
                tags
            ));
            runContext.metric(Counter.of(
                "request.bytes",
                httpClientContext.getEndpointDetails().getSentBytesCount(),
                tags
            ));
            runContext.metric(Counter.of(
                "response.bytes",
                httpClientContext.getEndpointDetails().getReceivedBytesCount(),
                tags
            ));
            runContext.metric(Counter.of(
                "response.count",
                httpClientContext.getEndpointDetails().getResponseCount(),
                tags
            ));
        } else {
            runContext.logger()
                .warn(
                    "Invalid response type HttpResponse => {}, HttpContext => {}",
                    response.getClass(),
                    context.getClass()
                );
        }
    }

    protected String[] tags(HttpRequest request, ClassicHttpResponse response) {
        ArrayList<String> tags = new ArrayList<>(Arrays.asList(
            "request.method", request.getMethod(),
            "request.scheme", request.getScheme(),
            "request.hostname", request.getAuthority().getHostName()
        ));

        if (response != null) {
            tags.addAll(
                Arrays.asList("response.code", String.valueOf(response.getCode()))
            );
        }

        return tags.toArray(String[]::new);
    }
}

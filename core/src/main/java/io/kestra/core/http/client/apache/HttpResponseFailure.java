package io.kestra.core.http.client.apache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.protocol.HttpContext;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.HttpService;
import io.kestra.core.http.client.HttpClientResponseException;

public final class HttpResponseFailure {
    private HttpResponseFailure() {
    }

    public static HttpClientResponseException exception(org.apache.hc.core5.http.HttpResponse response, HttpContext context) throws IOException {
        String error = "Failed http request with response code '" + response.getCode() + "'";

        if (response instanceof HttpEntityContainer httpEntity && httpEntity.getEntity() != null) {
            HttpService.HttpEntityCopy copy = HttpService.copy(httpEntity.getEntity());
            httpEntity.setEntity(copy);

            error += " and body:\n" + new String(copy.getBody(), StandardCharsets.UTF_8);
        }

        return new HttpClientResponseException(error, HttpResponse.from(response, context));
    }
}

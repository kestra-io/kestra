package io.kestra.core.http.client.apache;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.HttpService;
import io.kestra.core.http.client.HttpClientResponseException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FailedResponseInterceptor implements HttpResponseInterceptor {
    private final boolean allErrors;
    private List<Integer> statusCodes;

    public FailedResponseInterceptor() {
        this.allErrors = true;
    }

    public FailedResponseInterceptor(List<Integer> statusCodes) {
        this.statusCodes = statusCodes;
        this.allErrors = false;
    }


    @Override
    public void process(org.apache.hc.core5.http.HttpResponse response, EntityDetails entity, HttpContext context) throws HttpException, IOException {
        if (this.allErrors && response.getCode() >= 400) {
            this.raiseError(response, context);
        }

        if (this.statusCodes != null && !this.statusCodes.contains(response.getCode())) {
            this.raiseError(response, context);
        }
    }

    private void raiseError(org.apache.hc.core5.http.HttpResponse response, HttpContext context) throws IOException, HttpClientResponseException {
        String error = "Failed http request with response code '" + response.getCode() + "'";

        if (response instanceof HttpEntityContainer httpEntity && httpEntity.getEntity() != null) {
            HttpService.HttpEntityCopy copy = HttpService.copy(httpEntity.getEntity());
            httpEntity.setEntity(copy);

            error += " and body:\n" + new String(copy.getBody(), StandardCharsets.UTF_8);
        }

        throw new HttpClientResponseException(error, HttpResponse.from(response, context));
    }
}

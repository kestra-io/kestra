package io.kestra.core.http.client.apache;

import io.kestra.core.http.client.HttpClientResponseException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
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
        throw HttpResponseFailure.exception(response, context);
    }
}

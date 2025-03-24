package io.kestra.core.http.client.apache;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;

import java.io.IOException;

@AllArgsConstructor
public class LoggingResponseInterceptor extends AbstractLoggingInterceptor implements HttpResponseInterceptor {
    private Logger logger;
    private HttpConfiguration.LoggingType[] logs;

    @Override
    public void process(HttpResponse response, EntityDetails entity, HttpContext context) throws HttpException, IOException {
        if (logger.isDebugEnabled() && ArrayUtils.contains(logs, HttpConfiguration.LoggingType.RESPONSE_HEADERS)) {
            logger.debug(buildResponseEntry(response));
            logger.debug(buildHeadersEntry("response", response.getHeaders()));
        }

        if (logger.isTraceEnabled() && ArrayUtils.contains(logs, HttpConfiguration.LoggingType.RESPONSE_BODY)) {
            logger.trace(buildEntityEntry("response", response instanceof ClassicHttpResponse classicHttpResponse ? classicHttpResponse : null));
        }
    }

    private static String buildResponseEntry(HttpResponse response) {
        return "response:" +
            "\n    reason: "+ response.getReasonPhrase();
    }
}

package io.kestra.core.http.client.apache;

import io.kestra.core.http.HttpService;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;

import java.io.IOException;

@AllArgsConstructor
public class LoggingRequestInterceptor extends AbstractLoggingInterceptor implements HttpRequestInterceptor {
    private Logger logger;
    private HttpConfiguration.LoggingType[] logs;

    @Override
    public void process(HttpRequest request, EntityDetails entity, HttpContext context) throws HttpException, IOException {
        if (logger.isDebugEnabled() && ArrayUtils.contains(logs, HttpConfiguration.LoggingType.REQUEST_HEADERS)) {
            logger.debug(buildRequestEntry(request));
            logger.debug(buildHeadersEntry("request", request.getHeaders()));
        }

        if (logger.isTraceEnabled() && ArrayUtils.contains(logs, HttpConfiguration.LoggingType.REQUEST_BODY)) {
            logger.trace(buildEntityEntry("request", request instanceof ClassicHttpRequest classicHttpRequest ? classicHttpRequest : null));
        }
    }

    private String buildRequestEntry(HttpRequest request) {
        return "request:" +
            "\n    method: " + request.getMethod() +
            "\n    uri: " + HttpService.safeURI(request);
    }
}

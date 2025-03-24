package io.kestra.core.http;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.http.HttpHeaders;

@Builder(toBuilder = true)
@Value
public class HttpResponse<T> {
    /**
     * The status code for this response.
     */
    Status status;

    /**
     * The received response headers.
     */
    HttpHeaders headers;

    /**
     * The body. Depending on the type of {@code T}, the returned body
     * may represent the body after it was read (such as {@code byte[]}, or
     * {@code String}, or {@code Path}) or it may represent an object with
     * which the body is read, such as an {@link java.io.InputStream}.
     */
    T body;

    /**
     * The {@link EndpointDetail} corresponding to this response.
     */
    EndpointDetail endpointDetail;

    /**
     * The {@link HttpRequest} corresponding to this response.
     *
     * <p> The initiating {@code HttpRequest}. For example, if the initiating request was redirected, then the
     * request returned by this method will not have the redirected URI
     */
    HttpRequest request;

    public static HttpResponse<byte[]> from(org.apache.hc.core5.http.HttpResponse response, HttpContext context) throws IOException {
        return HttpResponse.<byte[]>builder()
            .status(Status.builder().code(response.getCode()).reason(response.getReasonPhrase()).build())
            .request(context instanceof HttpClientContext httpClientContext ?
                HttpRequest.from(httpClientContext.getRequest()) :
                null
            )
            .headers(HttpService.toHttpHeaders(response.getHeaders()))
            .body(response instanceof ClassicHttpResponse classicHttpResponse && classicHttpResponse.getEntity() != null ?
                IOUtils.toByteArray(classicHttpResponse.getEntity().getContent()) :
                null
            )
            .endpointDetail(context instanceof HttpClientContext httpClientContext ?
                HttpResponse.EndpointDetail.from(httpClientContext.getEndpointDetails()) :
                null
            )
            .build();
    }

    public static <T> HttpResponse<T> from(ClassicHttpResponse httpResponse, T body, HttpRequest request, HttpContext context) {
        return HttpResponse.<T>builder()
            .status(Status.builder().code(httpResponse.getCode()).reason(httpResponse.getReasonPhrase()).build())
            .request(request)
            .headers(HttpService.toHttpHeaders(httpResponse.getHeaders()))
            .body(body)
            .endpointDetail(context instanceof HttpClientContext httpClientContext ?
                HttpResponse.EndpointDetail.from(httpClientContext.getEndpointDetails()) :
                null
            )
            .build();
    }

    @Value
    @Builder
    public static class Status {
        int code;
        String reason;
    }

    @Value
    @Builder
    public static class EndpointDetail {
        SocketAddress remoteAddress;
        SocketAddress localAddress;

        /**
         * Gets the number of requests transferred over the connection,
         * 0 if not available.
         */
        Long requestCount;

        /**
         * Gets the number of responses transferred over the connection,
         * 0 if not available.
         */
        Long responseCount;

        /**
         * Gets the number of bytes transferred over the connection,
         * 0 if not available.
         */
        Long sentBytesCount;

        /**
         * Gets the number of bytes transferred over the connection,
         * 0 if not available.
         */
        Long receivedBytesCount;

        public static EndpointDetail from(EndpointDetails details) {
            return EndpointDetail.builder()
                .localAddress(details.getLocalAddress())
                .remoteAddress(details.getRemoteAddress())
                .requestCount(details.getRequestCount())
                .responseCount(details.getResponseCount())
                .sentBytesCount(details.getSentBytesCount())
                .receivedBytesCount(details.getReceivedBytesCount())
                .build();
        }
    }
}

package io.kestra.core.http;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public static HttpResponse<?> of(Status status) {
        return of(status, null, null);
    }

    public static <T> HttpResponse<T> of(T body) {
        return of(null, body, null);
    }

    public static <T> HttpResponse<T> of(Status status, T body) {
        return of(status, body, null);
    }

    public static <T> HttpResponse<T> of(@Nullable Status status, @Nullable T body, @Nullable String contentType) {
        HttpResponseBuilder<T> builder = HttpResponse.<T>builder()
            .status(status != null ? status : Status.OK);

        if (body != null) {
            builder.body(body);
        }

        if (contentType != null) {
            builder.headers(HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (s1, s2) -> true));
        }

        return builder.build();
    }

    public String contentType() {
        return Optional.ofNullable(this.getHeaders())
            .flatMap(headers -> headers.firstValue("Content-Type"))
            .orElseThrow(() -> new IllegalStateException("Response has no Content-Type header [" + this.getHeaders() + "]"));
    }

    /**
     * This data class is part of the plugin API. Changes are potentially breaking.
     */
    @Value
    @Builder
    @AllArgsConstructor
    public static class Status {
        public static Status CONTINUE = new Status(100, "Continue");
        public static Status SWITCHING_PROTOCOLS = new Status(101, "Switching Protocols");
        public static Status PROCESSING = new Status(102, "Processing");
        public static Status EARLY_HINTS = new Status(103, "Early Hints");
        public static Status OK = new Status(200, "Ok");
        public static Status CREATED = new Status(201, "Created");
        public static Status ACCEPTED = new Status(202, "Accepted");
        public static Status NON_AUTHORITATIVE_INFORMATION = new Status(203, "Non-Authoritative Information");
        public static Status NO_CONTENT = new Status(204, "No Content");
        public static Status RESET_CONTENT = new Status(205, "Reset Content");
        public static Status PARTIAL_CONTENT = new Status(206, "Partial Content");
        public static Status MULTI_STATUS = new Status(207, "Multi Status");
        public static Status ALREADY_IMPORTED = new Status(208, "Already imported");
        public static Status IM_USED = new Status(226, "IM Used");
        public static Status MULTIPLE_CHOICES = new Status(300, "Multiple Choices");
        public static Status MOVED_PERMANENTLY = new Status(301, "Moved Permanently");
        public static Status FOUND = new Status(302, "Found");
        public static Status SEE_OTHER = new Status(303, "See Other");
        public static Status NOT_MODIFIED = new Status(304, "Not Modified");
        public static Status USE_PROXY = new Status(305, "Use Proxy");
        public static Status SWITCH_PROXY = new Status(306, "Switch Proxy");
        public static Status TEMPORARY_REDIRECT = new Status(307, "Temporary Redirect");
        public static Status PERMANENT_REDIRECT = new Status(308, "Permanent Redirect");
        public static Status BAD_REQUEST = new Status(400, "Bad Request");
        public static Status UNAUTHORIZED = new Status(401, "Unauthorized");
        public static Status PAYMENT_REQUIRED = new Status(402, "Payment Required");
        public static Status FORBIDDEN = new Status(403, "Forbidden");
        public static Status NOT_FOUND = new Status(404, "Not Found");
        public static Status METHOD_NOT_ALLOWED = new Status(405, "Method Not Allowed");
        public static Status NOT_ACCEPTABLE = new Status(406, "Not Acceptable");
        public static Status PROXY_AUTHENTICATION_REQUIRED = new Status(407, "Proxy Authentication Required");
        public static Status REQUEST_TIMEOUT = new Status(408, "Request Timeout");
        public static Status CONFLICT = new Status(409, "Conflict");
        public static Status GONE = new Status(410, "Gone");
        public static Status LENGTH_REQUIRED = new Status(411, "Length Required");
        public static Status PRECONDITION_FAILED = new Status(412, "Precondition Failed");
        public static Status REQUEST_ENTITY_TOO_LARGE = new Status(413, "Request Entity Too Large");
        public static Status REQUEST_URI_TOO_LONG = new Status(414, "Request-URI Too Long");
        public static Status UNSUPPORTED_MEDIA_TYPE = new Status(415, "Unsupported Media Type");
        public static Status REQUESTED_RANGE_NOT_SATISFIABLE = new Status(416, "Requested Range Not Satisfiable");
        public static Status EXPECTATION_FAILED = new Status(417, "Expectation Failed");
        public static Status I_AM_A_TEAPOT = new Status(418, "I am a teapot");
        public static Status ENHANCE_YOUR_CALM = new Status(420, "Enhance your calm");
        public static Status MISDIRECTED_REQUEST = new Status(421, "Misdirected Request");
        public static Status UNPROCESSABLE_ENTITY = new Status(422, "Unprocessable Entity");
        public static Status LOCKED = new Status(423, "Locked");
        public static Status FAILED_DEPENDENCY = new Status(424, "Failed Dependency");
        public static Status TOO_EARLY = new Status(425, "Too Early");
        public static Status UPGRADE_REQUIRED = new Status(426, "Upgrade Required");
        public static Status PRECONDITION_REQUIRED = new Status(428, "Precondition Required");
        public static Status TOO_MANY_REQUESTS = new Status(429, "Too Many Requests");
        public static Status REQUEST_HEADER_FIELDS_TOO_LARGE = new Status(431, "Request Header Fields Too Large");
        public static Status NO_RESPONSE = new Status(444, "No Response");
        public static Status BLOCKED_BY_WINDOWS_PARENTAL_CONTROLS = new Status(450, "Blocked by Windows Parental Controls");
        public static Status UNAVAILABLE_FOR_LEGAL_REASONS = new Status(451, "Unavailable For Legal Reasons");
        public static Status REQUEST_HEADER_TOO_LARGE = new Status(494, "Request Header Too Large");
        public static Status INTERNAL_SERVER_ERROR = new Status(500, "Internal Server Error");
        public static Status NOT_IMPLEMENTED = new Status(501, "Not Implemented");
        public static Status BAD_GATEWAY = new Status(502, "Bad Gateway");
        public static Status SERVICE_UNAVAILABLE = new Status(503, "Service Unavailable");
        public static Status GATEWAY_TIMEOUT = new Status(504, "Gateway Timeout");
        public static Status HTTP_VERSION_NOT_SUPPORTED = new Status(505, "HTTP Version Not Supported");
        public static Status VARIANT_ALSO_NEGOTIATES = new Status(506, "Variant Also Negotiates");
        public static Status INSUFFICIENT_STORAGE = new Status(507, "Insufficient Storage");
        public static Status LOOP_DETECTED = new Status(508, "Loop Detected");
        public static Status BANDWIDTH_LIMIT_EXCEEDED = new Status(509, "Bandwidth Limit Exceeded");
        public static Status NOT_EXTENDED = new Status(510, "Not Extended");
        public static Status NETWORK_AUTHENTICATION_REQUIRED = new Status(511, "Network Authentication Required");
        public static Status CONNECTION_TIMED_OUT = new Status(522, "Connection Timed Out");

        int code;
        String reason;

        public static Status valueOf(int code) {
            return switch (code) {
                case 100 -> CONTINUE;
                case 101 -> SWITCHING_PROTOCOLS;
                case 102 -> PROCESSING;
                case 103 -> EARLY_HINTS;
                case 200 -> OK;
                case 201 -> CREATED;
                case 202 -> ACCEPTED;
                case 203 -> NON_AUTHORITATIVE_INFORMATION;
                case 204 -> NO_CONTENT;
                case 205 -> RESET_CONTENT;
                case 206 -> PARTIAL_CONTENT;
                case 207 -> MULTI_STATUS;
                case 208 -> ALREADY_IMPORTED;
                case 226 -> IM_USED;
                case 300 -> MULTIPLE_CHOICES;
                case 301 -> MOVED_PERMANENTLY;
                case 302 -> FOUND;
                case 303 -> SEE_OTHER;
                case 304 -> NOT_MODIFIED;
                case 305 -> USE_PROXY;
                case 306 -> SWITCH_PROXY;
                case 307 -> TEMPORARY_REDIRECT;
                case 308 -> PERMANENT_REDIRECT;
                case 400 -> BAD_REQUEST;
                case 401 -> UNAUTHORIZED;
                case 402 -> PAYMENT_REQUIRED;
                case 403 -> FORBIDDEN;
                case 404 -> NOT_FOUND;
                case 405 -> METHOD_NOT_ALLOWED;
                case 406 -> NOT_ACCEPTABLE;
                case 407 -> PROXY_AUTHENTICATION_REQUIRED;
                case 408 -> REQUEST_TIMEOUT;
                case 409 -> CONFLICT;
                case 410 -> GONE;
                case 411 -> LENGTH_REQUIRED;
                case 412 -> PRECONDITION_FAILED;
                case 413 -> REQUEST_ENTITY_TOO_LARGE;
                case 414 -> REQUEST_URI_TOO_LONG;
                case 415 -> UNSUPPORTED_MEDIA_TYPE;
                case 416 -> REQUESTED_RANGE_NOT_SATISFIABLE;
                case 417 -> EXPECTATION_FAILED;
                case 418 -> I_AM_A_TEAPOT;
                case 420 -> ENHANCE_YOUR_CALM;
                case 421 -> MISDIRECTED_REQUEST;
                case 422 -> UNPROCESSABLE_ENTITY;
                case 423 -> LOCKED;
                case 424 -> FAILED_DEPENDENCY;
                case 425 -> TOO_EARLY;
                case 426 -> UPGRADE_REQUIRED;
                case 428 -> PRECONDITION_REQUIRED;
                case 429 -> TOO_MANY_REQUESTS;
                case 431 -> REQUEST_HEADER_FIELDS_TOO_LARGE;
                case 444 -> NO_RESPONSE;
                case 450 -> BLOCKED_BY_WINDOWS_PARENTAL_CONTROLS;
                case 451 -> UNAVAILABLE_FOR_LEGAL_REASONS;
                case 494 -> REQUEST_HEADER_TOO_LARGE;
                case 500 -> INTERNAL_SERVER_ERROR;
                case 501 -> NOT_IMPLEMENTED;
                case 502 -> BAD_GATEWAY;
                case 503 -> SERVICE_UNAVAILABLE;
                case 504 -> GATEWAY_TIMEOUT;
                case 505 -> HTTP_VERSION_NOT_SUPPORTED;
                case 506 -> VARIANT_ALSO_NEGOTIATES;
                case 507 -> INSUFFICIENT_STORAGE;
                case 508 -> LOOP_DETECTED;
                case 509 -> BANDWIDTH_LIMIT_EXCEEDED;
                case 510 -> NOT_EXTENDED;
                case 511 -> NETWORK_AUTHENTICATION_REQUIRED;
                case 522 -> CONNECTION_TIMED_OUT;
                default -> throw new IllegalArgumentException("Invalid HTTP status code: " + code);
            };
        }
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

package io.kestra.core.http.client.apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import io.kestra.core.http.client.HttpClientResponseException;

class HttpResponseFailureTest {
    @Test
    void shouldEscapeLineBreaksAndPreserveResponseBody() throws Exception {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(404, "Not Found");
        String body = "first\r\nsecond\nthird\rfourth";
        response.setEntity(new StringEntity(body));

        HttpClientResponseException exception = HttpResponseFailure.exception(response, null);

        assertThat(exception.getMessage()).isEqualTo("Failed http request with response code '404' and body: first\\r\\nsecond\\nthird\\rfourth");
        assertThat(exception.getResponse().getBody()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldBuildMessageWithoutBodyWhenResponseHasNoEntity() throws Exception {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(404, "Not Found");

        HttpClientResponseException exception = HttpResponseFailure.exception(response, null);

        assertThat(exception.getMessage()).isEqualTo("Failed http request with response code '404'");
        assertThat(exception.getResponse().getBody()).isNull();
    }
}

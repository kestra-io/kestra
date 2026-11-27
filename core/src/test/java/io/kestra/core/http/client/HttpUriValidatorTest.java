package io.kestra.core.http.client;

import java.net.InetAddress;
import java.net.URI;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.http.WebsocketRequest;
import jakarta.inject.Inject;

import java.util.Map;
import org.junit.jupiter.api.Test;
import io.kestra.core.junit.annotations.KestraTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class HttpUriValidatorTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldParseIpv4Literal() {
        assertThat(HttpUriValidator.parseIpLiteral("127.0.0.1"))
                .isPresent()
                .get()
                .extracting(InetAddress::getHostAddress)
                .isEqualTo("127.0.0.1");
    }

    @Test
    void shouldParseIpv6Literal() {
        assertThat(HttpUriValidator.parseIpLiteral("::1"))
                .isPresent();
    }

    @Test
    void shouldReturnEmptyForInvalidIpLiteral() {
        assertThat(HttpUriValidator.parseIpLiteral("example.com"))
                .isEmpty();
    }

    @Test
    void shouldRejectAddressInsideDeniedCidrRange() throws Exception {
        InetAddress address = InetAddress.getByName("127.0.0.1");

        assertThatThrownBy(() -> HttpUriValidator.rejectDeniedAddresses(
                "localhost",
                new InetAddress[] { address },
                java.util.List.of("127.0.0.0/8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("localhost")
                .hasMessageContaining("configured denied list");
    }

    @Test
    void shouldAllowAddressOutsideDeniedCidrRange() throws Exception {
        InetAddress address = InetAddress.getByName("192.0.2.10");

        HttpUriValidator.rejectDeniedAddresses(
                "example.com",
                new InetAddress[] { address },
                java.util.List.of("127.0.0.0/8"));
    }

    @Test
    void shouldRejectIpv6LoopbackAddress() throws Exception {
        InetAddress address = InetAddress.getByName("::1");

        assertThatThrownBy(() -> HttpUriValidator.rejectDeniedAddresses(
                "localhost",
                new InetAddress[] { address },
                java.util.List.of("::1/128")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configured denied list");
    }

    @Test
    void shouldHandleEmptyResolvedAddresses() {
        HttpUriValidator.rejectDeniedAddresses(
                "example.com",
                new InetAddress[] {},
                java.util.List.of("127.0.0.1"));
    }
}

package io.kestra.core.http.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

class UriAccessGuardTest {
    @Test
    void shouldAllowAnyUriWhenBothListsAreEmpty() {
        assertThat(UriAccessGuard.findViolation(URI.create("https://169.254.169.254/"), List.of(), List.of())).isEmpty();
    }

    @Test
    void shouldRejectUriWhenAddressIsInDeniedCidr() {
        assertThat(UriAccessGuard.findViolation(URI.create("https://169.254.169.254/latest"), List.of(), List.of("169.254.0.0/16"))).isPresent();
    }

    @Test
    void shouldRejectHostNameResolvingToDeniedAddress() {
        assertThat(UriAccessGuard.findViolation(URI.create("https://localhost:8443/"), List.of(), List.of("127.0.0.0/8", "::1/128"))).isPresent();
    }

    @Test
    void shouldRejectUriWhenNotInAllowedList() {
        assertThat(UriAccessGuard.findViolation(URI.create("https://evil.example/"), List.of("*.kestra.internal"), List.of())).isPresent();
    }

    @Test
    void shouldAllowUriMatchingAllowedWildcard() {
        assertThat(UriAccessGuard.findViolation(URI.create("https://prod.kestra.internal/"), List.of("*.kestra.internal"), List.of("10.0.0.0/8"))).isEmpty();
    }

    @Test
    void shouldAcceptUriAsString() {
        assertThat(UriAccessGuard.findViolation("https://169.254.169.254/latest", List.of(), List.of("169.254.0.0/16"))).isPresent();
    }

    @Test
    void shouldRejectInvalidUriString() {
        assertThat(UriAccessGuard.findViolation("not a uri", List.of(), List.of())).isPresent();
    }

    @Test
    void shouldAllowAnyUriWhenListsAreNull() {
        assertThat(UriAccessGuard.findViolation("https://169.254.169.254/latest", (UriAccessLists) null)).isEmpty();
        assertThat(UriAccessGuard.findViolation("https://169.254.169.254/latest", new UriAccessLists() {
            @Override
            public List<String> allowedList() {
                return null;
            }

            @Override
            public List<String> deniedList() {
                return null;
            }
        })).isEmpty();
    }
}

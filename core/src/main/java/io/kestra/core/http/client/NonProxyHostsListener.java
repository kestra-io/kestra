package io.kestra.core.http.client;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.client.HttpClientConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Makes every Micronaut HTTP client configured with a {@code proxy-address} bypass the proxy for the hosts listed in the
 * JVM {@code http.nonProxyHosts} property, which Micronaut otherwise ignores.
 */
@Singleton
public class NonProxyHostsListener implements BeanCreatedEventListener<HttpClientConfiguration> {
    private static final List<String> NON_PROXY_HOSTS = parse(System.getProperty("http.nonProxyHosts"));

    private final List<String> nonProxyHosts;

    @Inject
    public NonProxyHostsListener() {
        this(NON_PROXY_HOSTS);
    }

    NonProxyHostsListener(List<String> nonProxyHosts) {
        this.nonProxyHosts = nonProxyHosts;
    }

    @Override
    public HttpClientConfiguration onCreated(BeanCreatedEvent<HttpClientConfiguration> event) {
        HttpClientConfiguration configuration = event.getBean();
        if (configuration.getProxyAddress().isPresent() && configuration.getProxySelector().isEmpty()) {
            configuration.setProxySelector(new NonProxyHostsSelector(configuration, nonProxyHosts));
        }
        return configuration;
    }

    static List<String> parse(String nonProxyHosts) {
        if (nonProxyHosts == null || nonProxyHosts.isBlank()) {
            return List.of();
        }
        return Arrays.stream(nonProxyHosts.split("\\|"))
            .map(pattern -> pattern.trim().toLowerCase(Locale.ROOT))
            .filter(pattern -> !pattern.isEmpty())
            .toList();
    }

    private static boolean matches(String host, String pattern) {
        if (pattern.length() > 1 && pattern.startsWith("*") && pattern.endsWith("*")) {
            return host.contains(pattern.substring(1, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return host.endsWith(pattern.substring(1));
        }
        if (pattern.endsWith("*")) {
            return host.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return host.equals(pattern);
    }

    private static final class NonProxyHostsSelector extends ProxySelector {
        private final HttpClientConfiguration configuration;
        private final List<String> nonProxyHosts;

        private NonProxyHostsSelector(HttpClientConfiguration configuration, List<String> nonProxyHosts) {
            this.configuration = configuration;
            this.nonProxyHosts = nonProxyHosts;
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (isNonProxyHost(uri.getHost())) {
                return List.of(Proxy.NO_PROXY);
            }
            return List.of(configuration.getProxyAddress()
                .map(address -> new Proxy(configuration.getProxyType(), address))
                .orElse(Proxy.NO_PROXY));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException exception) {
        }

        private boolean isNonProxyHost(String host) {
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return nonProxyHosts.stream().anyMatch(pattern -> matches(normalizedHost, pattern));
        }
    }
}

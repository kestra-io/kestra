package io.kestra.core.http.client;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;

import org.junit.jupiter.api.Test;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NonProxyHostsListenerTest {
    private static final InetSocketAddress PROXY_ADDRESS = InetSocketAddress.createUnresolved("squid", 3128);
    private static final Proxy PROXY = new Proxy(Proxy.Type.HTTP, PROXY_ADDRESS);

    @Test
    void shouldBypassProxyForNonProxyHostsWhenProxyAddressIsConfigured() {
        HttpClientConfiguration configuration = onCreated(proxiedConfiguration());

        assertThat(configuration.resolveProxy(false, "keycloak", 8080)).isEqualTo(Proxy.NO_PROXY);
        assertThat(configuration.resolveProxy(true, "idp.corp", 443)).isEqualTo(Proxy.NO_PROXY);
        assertThat(configuration.resolveProxy(true, "api.kestra.io", 443)).isEqualTo(PROXY);
    }

    @Test
    void shouldBypassProxyForHostsContainingThePatternWhenItHasAWildcardAtBothEnds() {
        HttpClientConfiguration configuration = onCreated(proxiedConfiguration());

        assertThat(configuration.resolveProxy(true, "idp.internal.example.com", 443)).isEqualTo(Proxy.NO_PROXY);
        assertThat(configuration.resolveProxy(true, "internal.example.com", 443)).isEqualTo(PROXY);
    }

    @Test
    void shouldKeepProxySelectorWhenOneIsAlreadyConfigured() {
        DefaultHttpClientConfiguration configuration = proxiedConfiguration();
        ProxySelector selector = mock(ProxySelector.class);
        configuration.setProxySelector(selector);

        assertThat(onCreated(configuration).getProxySelector()).containsSame(selector);
    }

    @Test
    void shouldNotInstallProxySelectorWhenNoProxyAddressIsConfigured() {
        assertThat(onCreated(new DefaultHttpClientConfiguration()).getProxySelector()).isEmpty();
    }

    private static DefaultHttpClientConfiguration proxiedConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setProxyType(Proxy.Type.HTTP);
        configuration.setProxyAddress(PROXY_ADDRESS);
        return configuration;
    }

    @SuppressWarnings("unchecked")
    private static HttpClientConfiguration onCreated(HttpClientConfiguration configuration) {
        BeanCreatedEvent<HttpClientConfiguration> event = mock(BeanCreatedEvent.class);
        when(event.getBean()).thenReturn(configuration);
        return new NonProxyHostsListener(NonProxyHostsListener.parse("localhost|keycloak|*.corp|*.internal.*")).onCreated(event);
    }
}

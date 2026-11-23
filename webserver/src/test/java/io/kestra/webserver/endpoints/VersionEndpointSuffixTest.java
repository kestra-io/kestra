package io.kestra.webserver.endpoints;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.controllers.domain.ServerInfo;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = {"test", VersionEndpointSuffixTest.ENVIRONMENT})
class VersionEndpointSuffixTest {
    static final String ENVIRONMENT = "custom-version-suffix";

    @Inject
    private VersionEndpoint versionEndpoint;

    @Inject
    private VersionProvider versionProvider;

    @Test
    void shouldUseOverriddenSuffixWhenSubclassOverridesVersionSuffix() {
        ServerInfo serverInfo = Mono.from(versionEndpoint.version()).block();

        assertThat(versionEndpoint).isInstanceOf(CustomSuffixVersionEndpoint.class);
        assertThat(serverInfo).isNotNull();
        assertThat(serverInfo.version()).isEqualTo(versionProvider.getVersion() + "-custom");
    }

    @Singleton
    @Replaces(VersionEndpoint.class)
    @Requires(env = ENVIRONMENT)
    static class CustomSuffixVersionEndpoint extends VersionEndpoint {
        @Override
        protected String versionSuffix() {
            return "-custom";
        }
    }
}

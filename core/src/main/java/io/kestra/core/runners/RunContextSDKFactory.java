package io.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class RunContextSDKFactory {
    public SDK create(ApplicationContext applicationContext, RunContext runContext) {
        return new SDKImpl(applicationContext);
    }

    static class SDKImpl implements SDK {
        private static final String AUTH_PROP = "kestra.tasks.sdk.authentication";
        private static final String API_TOKEN_PROP = AUTH_PROP + ".api-token";
        private static final String USERNAME_PROP = AUTH_PROP + ".username";
        private static final String PASSWORD_PROP = AUTH_PROP + ".password";

        private final Auth sdkAuthentication;

        SDKImpl(ApplicationContext applicationContext) {
            this.sdkAuthentication = applicationContext.getProperty(API_TOKEN_PROP, String.class)
                .map(it -> new SDK.Auth(Optional.of(it), Optional.empty(), Optional.empty()))
                .orElseGet(() -> {
                    Optional<String> maybeUserName = applicationContext.getProperty(USERNAME_PROP, String.class);
                    Optional<String> maybePassword = applicationContext.getProperty(PASSWORD_PROP, String.class);
                    if (maybePassword.isPresent() && maybeUserName.isPresent()) {
                        return new SDK.Auth(Optional.empty(), maybeUserName, maybePassword);
                    }
                    if (maybeUserName.isPresent() || maybePassword.isPresent()) {
                        throw new IllegalArgumentException("Both username and password must be provided if either is present: please configure both '" + USERNAME_PROP + "' and '" + PASSWORD_PROP + "' properties");
                    }
                    return null;
                });
        }

        @Override
        public Optional<Auth> defaultAuthentication() {
            return Optional.ofNullable(this.sdkAuthentication);
        }
    }
}

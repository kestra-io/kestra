package io.kestra.webserver.services;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.AuthUtils;
import io.kestra.webserver.models.events.OssAuthEvent;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.*;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// Force an eager crash of the app in case of misconfigured basic authentication (e.g. invalid username)
@Context
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class BasicAuthService {
    public static final String BASIC_AUTH_SETTINGS_KEY = "kestra.server.basic-auth";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$");
    private static final int EMAIL_PASSWORD_MAX_LEN = 256;

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Inject
    private BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private InstanceService instanceService;

    @Inject
    private ApplicationEventPublisher<OssAuthEvent> ossAuthEventPublisher;

    @PostConstruct
    private void init() {
        if (Boolean.TRUE.equals(this.basicAuthConfiguration.getEnabled())) {
            this.save(this.basicAuthConfiguration);
        } else if (Boolean.FALSE.equals(this.basicAuthConfiguration.getEnabled())) {
            this.unsecure();
        }
    }

    public boolean isEnabled() {
        BasicAuthConfiguration basicAuthConfiguration = configuration();
        if (basicAuthConfiguration == null) {
            return false;
        }

        return Boolean.TRUE.equals(basicAuthConfiguration.getEnabled()) && basicAuthConfiguration.getUsername() != null && basicAuthConfiguration.getPassword() != null;
    }

    public void save(BasicAuthConfiguration basicAuthConfiguration) {
        save(null, basicAuthConfiguration);
    }

    public void save(String uid, BasicAuthConfiguration basicAuthConfiguration) {
        if (basicAuthConfiguration.getUsername() != null && !EMAIL_PATTERN.matcher(basicAuthConfiguration.getUsername()).matches()) {
            throw new IllegalArgumentException("Invalid username for Basic Authentication. Please provide a valid email address.");
        }

        if (basicAuthConfiguration.getPassword() == null) {
            throw new IllegalArgumentException("No password set for Basic Authentication. Please provide a password.");
        }

        if (basicAuthConfiguration.getUsername().length() > EMAIL_PASSWORD_MAX_LEN ||
            basicAuthConfiguration.password.length() > EMAIL_PASSWORD_MAX_LEN) {
            throw new IllegalArgumentException("The length of email or password should not exceed 256.");
        }


        SaltedBasicAuthConfiguration previousConfiguration = this.configuration();
        String salt = previousConfiguration == null
            ? null
            : previousConfiguration.getSalt();
        SaltedBasicAuthConfiguration saltedNewConfiguration = new SaltedBasicAuthConfiguration(
            salt,
            basicAuthConfiguration
        );
        if (!saltedNewConfiguration.equals(previousConfiguration)) {
            settingRepository.save(
                Setting.builder()
                    .key(BASIC_AUTH_SETTINGS_KEY)
                    .value(saltedNewConfiguration)
                    .build()
            );

            ossAuthEventPublisher.publishEventAsync(
                OssAuthEvent.builder()
                    .uid(uid)
                    .iid(instanceService.fetch())
                    .date(Instant.now())
                    .ossAuth(OssAuthEvent.OssAuth.builder()
                        .email(basicAuthConfiguration.getUsername())
                        .build()
                    ).build()
            );
        }
    }

    public void unsecure() {
        BasicAuthConfiguration configuration = configuration();
        if (configuration == null || Boolean.FALSE.equals(configuration.getEnabled())) {
            return;
        }

        settingRepository.save(Setting.builder()
            .key(BASIC_AUTH_SETTINGS_KEY)
            .value(configuration.withEnabled(false))
            .build());
    }

    public SaltedBasicAuthConfiguration configuration() {
        return settingRepository.findByKey(BASIC_AUTH_SETTINGS_KEY)
            .map(Setting::getValue)
            .map(value -> JacksonMapper.toMap(value, SaltedBasicAuthConfiguration.class))
            .orElse(null);
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    @ConfigurationProperties("kestra.server.basic-auth")
    public static class BasicAuthConfiguration {
        @With
        private Boolean enabled;
        private String username;
        protected String password;
        private String realm;
        private List<String> openUrls;

        @SuppressWarnings("MnInjectionPoints")
        @ConfigurationInject
        public BasicAuthConfiguration(
            @Nullable Boolean enabled,
            @Nullable String username,
            @Nullable String password,
            @Nullable String realm,
            @Nullable List<String> openUrls
        ) {
            this.enabled = enabled;
            this.username = username;
            this.password = password;
            this.realm = Optional.ofNullable(realm).orElse("Kestra");
            this.openUrls = Optional.ofNullable(openUrls).orElse(Collections.emptyList());
        }

        public BasicAuthConfiguration(
            String username,
            String password
        ) {
            this(true, username, password, null, null);
        }

        public BasicAuthConfiguration(BasicAuthConfiguration basicAuthConfiguration) {
            if (basicAuthConfiguration != null) {
                this.enabled = basicAuthConfiguration.getEnabled();
                this.username = basicAuthConfiguration.getUsername();
                this.password = basicAuthConfiguration.getPassword();
                this.realm = basicAuthConfiguration.getRealm();
                this.openUrls = basicAuthConfiguration.getOpenUrls();
            }
        }

        @VisibleForTesting
        BasicAuthConfiguration withUsernamePassword(String username, String password) {
            return new BasicAuthConfiguration(
                this.enabled,
                username,
                password,
                this.realm,
                this.openUrls
            );
        }
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class SaltedBasicAuthConfiguration extends BasicAuthConfiguration {
        private final String salt;

        public SaltedBasicAuthConfiguration(String salt, BasicAuthConfiguration basicAuthConfiguration) {
            super(basicAuthConfiguration);
            this.salt = salt == null
                ? AuthUtils.generateSalt()
                : salt;
            this.password = AuthUtils.encodePassword(this.salt, basicAuthConfiguration.getPassword());
        }
    }
}
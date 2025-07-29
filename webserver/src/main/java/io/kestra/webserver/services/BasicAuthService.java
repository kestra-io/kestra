package io.kestra.webserver.services;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.exceptions.ValidationErrorException;
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
import java.util.ArrayList;
import lombok.*;

import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

@Context
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class BasicAuthService {
    public static final String BASIC_AUTH_SETTINGS_KEY = "kestra.server.basic-auth";
    public static final String BASIC_AUTH_ERROR_CONFIG = "kestra.server.authentication-configuration-error";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?=.{8,})(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).*");
    private static final int EMAIL_PASSWORD_MAX_LEN = 256;

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Inject
    BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private InstanceService instanceService;

    @Inject
    private ApplicationEventPublisher<OssAuthEvent> ossAuthEventPublisher;

    public BasicAuthService() {}

    @PostConstruct
    protected void init() {
        if (basicAuthConfiguration == null ||
            (StringUtils.isBlank(basicAuthConfiguration.getUsername()) && StringUtils.isBlank(basicAuthConfiguration.getPassword()))){
            return;
        }
        try {
            save(basicAuthConfiguration);
            if (settingRepository.findByKey(BASIC_AUTH_ERROR_CONFIG).isPresent()) {
                settingRepository.delete(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).build());
            }
        } catch (ValidationErrorException e){
            settingRepository.save(Setting.builder()
                .key(BASIC_AUTH_ERROR_CONFIG)
                .value(e.getInvalids())
                .build());
        }
    }

    public void save(BasicAuthConfiguration basicAuthConfiguration) {
        save(null, basicAuthConfiguration);
    }

    public void save(String uid, BasicAuthConfiguration basicAuthConfiguration) {
        List<String> validationErrors = new ArrayList<>();

        if (basicAuthConfiguration.getUsername() != null && !EMAIL_PATTERN.matcher(basicAuthConfiguration.getUsername()).matches()) {
            validationErrors.add("Invalid username for Basic Authentication. Please provide a valid email address.");
        }

        if (basicAuthConfiguration.getUsername() == null) {
            validationErrors.add("No user name set for Basic Authentication. Please provide a user name.");
        }

        if (basicAuthConfiguration.getPassword() == null) {
            validationErrors.add("No password set for Basic Authentication. Please provide a password.");
        }

        if (basicAuthConfiguration.getPassword() != null && !PASSWORD_PATTERN.matcher(basicAuthConfiguration.getPassword()).matches()) {
            validationErrors.add("Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number");
        }

        if ((basicAuthConfiguration.getUsername() != null && basicAuthConfiguration.getUsername().length() > EMAIL_PASSWORD_MAX_LEN) ||
            (basicAuthConfiguration.getPassword() != null && basicAuthConfiguration.getPassword().length() > EMAIL_PASSWORD_MAX_LEN)) {
            validationErrors.add("The length of email or password should not exceed 256 characters.");
        }

        if (!validationErrors.isEmpty()){
            throw new ValidationErrorException(validationErrors);
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

    public List<String> validationErrors() {
        return settingRepository.findByKey(BASIC_AUTH_ERROR_CONFIG)
            .map(Setting::getValue)
            .map(JacksonMapper::toList)
            .orElse(List.of());
    }

    public SaltedBasicAuthConfiguration configuration() {
        return settingRepository.findByKey(BASIC_AUTH_SETTINGS_KEY)
            .map(Setting::getValue)
            .map(value -> JacksonMapper.ofJson(false).convertValue(value, SaltedBasicAuthConfiguration.class))
            .orElse(null);
    }

    public boolean isBasicAuthInitialized(){

        SaltedBasicAuthConfiguration configuration = configuration();

        return configuration != null &&
               !StringUtils.isBlank(configuration.getUsername()) &&
               !StringUtils.isBlank(configuration.getPassword());
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    @ConfigurationProperties("kestra.server.basic-auth")
    public static class BasicAuthConfiguration {
        private String username;
        protected String password;
        private String realm;
        private List<String> openUrls;

        @SuppressWarnings("MnInjectionPoints")
        @ConfigurationInject
        public BasicAuthConfiguration(
            @Nullable String username,
            @Nullable String password,
            @Nullable String realm,
            @Nullable List<String> openUrls
        ) {
            this.username = username;
            this.password = password;
            this.realm = Optional.ofNullable(realm).orElse("Kestra");
            this.openUrls = Optional.ofNullable(openUrls).orElse(Collections.emptyList());
        }

        public BasicAuthConfiguration(
            String username,
            String password
        ) {
            this(username, password, null, null);
        }

        public BasicAuthConfiguration(BasicAuthConfiguration basicAuthConfiguration) {
            if (basicAuthConfiguration != null) {
                this.username = basicAuthConfiguration.getUsername();
                this.password = basicAuthConfiguration.getPassword();
                this.realm = basicAuthConfiguration.getRealm();
                this.openUrls = basicAuthConfiguration.getOpenUrls();
            }
        }

        @VisibleForTesting
        BasicAuthConfiguration withUsernamePassword(String username, String password) {
            return new BasicAuthConfiguration(
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
        private String salt;

        public SaltedBasicAuthConfiguration(String salt, BasicAuthConfiguration basicAuthConfiguration) {
            super(basicAuthConfiguration);
            this.salt = salt == null
                ? AuthUtils.generateSalt()
                : salt;
            this.password = AuthUtils.encodePassword(this.salt, basicAuthConfiguration.getPassword());
        }

        public SaltedBasicAuthConfiguration() {
            super();
        }
    }
}
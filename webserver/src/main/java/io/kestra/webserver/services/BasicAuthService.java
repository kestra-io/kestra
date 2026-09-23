package io.kestra.webserver.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Context
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true") // don't load this in EE
public class BasicAuthService {
    public static final String BASIC_AUTH_SETTINGS_KEY = "kestra.server.basic-auth";
    public static final String BASIC_AUTH_ERROR_CONFIG = "kestra.server.authentication-configuration-error";
    public static final String BASIC_AUTH_COOKIE_NAME = "BASIC_AUTH";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?=.{8,})(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).*");
    private static final int EMAIL_PASSWORD_MAX_LEN = 256;

    /**
     * The last successfully verified token, keyed by both its SHA-256 and a fingerprint of the
     * credentials it was verified against, or {@code null} if not yet verified or invalidated.
     * The fingerprint is re-derived from {@link #configuration()} — already fetched fresh on
     * every call — on every fast-path check, so a credential change on any webserver node sharing
     * the same settings store (not just this one) invalidates the cache on this node's very next
     * request, without any cross-node signal. A cache keyed on the token hash alone would let a
     * password already revoked on another node keep authenticating here indefinitely.
     * Cleared by {@link #save} whenever credentials change so an old token stops working
     * immediately on this node too.
     */
    private final AtomicReference<CachedToken> lastVerifiedToken = new AtomicReference<>();

    private record CachedToken(String tokenSha256, String credentialsFingerprint) {
    }

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Inject
    @VisibleForTesting
    BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private InstanceService instanceService;

    @Inject
    private ApplicationEventPublisher<OssAuthEvent> ossAuthEventPublisher;

    public BasicAuthService(SettingRepositoryInterface settingRepository, BasicAuthConfiguration basicAuthConfiguration, InstanceService instanceService,
        ApplicationEventPublisher<OssAuthEvent> ossAuthEventPublisher) {
        this.settingRepository = settingRepository;
        this.basicAuthConfiguration = basicAuthConfiguration;
        this.instanceService = instanceService;
        this.ossAuthEventPublisher = ossAuthEventPublisher;
    }

    public BasicAuthService() {
    }

    @VisibleForTesting
    @PostConstruct
    public void init() {
        // Compatibility layer: upgrade any legacy SHA-512 stored password to bcrypt.
        // This replaces the JDBC migration that is unavailable in the 1.3 branch.
        migrateLegacyPassword();

        if (
            basicAuthConfiguration == null ||
                (StringUtils.isBlank(basicAuthConfiguration.getUsername()) && StringUtils.isBlank(basicAuthConfiguration.getPassword()))
        ) {
            return;
        }
        try {
            // save configured default credentials
            save(
                new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword())
            );
            if (settingRepository.findByKey(BASIC_AUTH_ERROR_CONFIG).isPresent()) {
                settingRepository.delete(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).build());
            }
        } catch (ValidationErrorException e) {
            settingRepository.save(
                Setting.builder()
                    .key(BASIC_AUTH_ERROR_CONFIG)
                    .value(e.getInvalids())
                    .build()
            );
        }
    }

    /**
     * If the stored password is a legacy SHA-512 hex string (does not start with the bcrypt
     * modular-crypt prefix {@code $2}), wraps it in bcrypt in-place.  No plaintext is needed:
     * the SHA-512 digest itself becomes the bcrypt input, matching the algorithm used by
     * {@link AuthUtils#hashPassword} so that subsequent verifications work transparently.
     */
    private void migrateLegacyPassword() {
        var credentials = this.configuration().credentials();
        if (credentials == null || credentials.getPassword() == null || credentials.getPassword().startsWith("$2")) {
            return;
        }
        String bcryptHash = AuthUtils.bcryptDigest(credentials.getPassword());
        settingRepository.save(
            Setting.builder()
                .key(BASIC_AUTH_SETTINGS_KEY)
                .value(new SaltedBasicAuthCredentials(credentials.getSalt(), credentials.getUsername(), bcryptHash))
                .build()
        );
        lastVerifiedToken.set(null);
    }

    public void save(BasicAuthCredentials basicAuthCredentials) {
        List<String> validationErrors = new ArrayList<>();

        if (basicAuthCredentials.getUsername() != null && !EMAIL_PATTERN.matcher(basicAuthCredentials.getUsername()).matches()) {
            validationErrors.add("Invalid username for Basic Authentication. Please provide a valid email address.");
        }

        if (basicAuthCredentials.getUsername() == null) {
            validationErrors.add("No user name set for Basic Authentication. Please provide a user name.");
        }

        if (basicAuthCredentials.getPassword() == null) {
            validationErrors.add("No password set for Basic Authentication. Please provide a password.");
        }

        if (basicAuthCredentials.getPassword() != null && !PASSWORD_PATTERN.matcher(basicAuthCredentials.getPassword()).matches()) {
            validationErrors.add("Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number");
        }

        if (
            (basicAuthCredentials.getUsername() != null && basicAuthCredentials.getUsername().length() > EMAIL_PASSWORD_MAX_LEN) ||
                (basicAuthCredentials.getPassword() != null && basicAuthCredentials.getPassword().length() > EMAIL_PASSWORD_MAX_LEN)
        ) {
            validationErrors.add("The length of email or password should not exceed 256 characters.");
        }

        if (!validationErrors.isEmpty()) {
            throw new ValidationErrorException(validationErrors);
        }

        var previousConfiguredCredentials = this.configuration().credentials();
        String salt = previousConfiguredCredentials == null
            ? null
            : previousConfiguredCredentials.getSalt();

        // bcrypt is non-deterministic, so we cannot compare hashes directly.
        // Use matches() to determine whether the credentials actually changed.
        boolean unchanged = previousConfiguredCredentials != null
            && previousConfiguredCredentials.getUsername().equals(basicAuthCredentials.getUsername())
            && AuthUtils.matches(previousConfiguredCredentials.getSalt(), basicAuthCredentials.getPassword(), previousConfiguredCredentials.getPassword());

        if (!unchanged) {
            SaltedBasicAuthCredentials saltedNewConfiguration = SaltedBasicAuthCredentials.salt(
                salt,
                basicAuthCredentials.getUsername(),
                basicAuthCredentials.getPassword()
            );
            settingRepository.save(
                Setting.builder()
                    .key(BASIC_AUTH_SETTINGS_KEY)
                    .value(saltedNewConfiguration)
                    .build()
            );

            // Invalidate the token cache so an old password stops working immediately.
            lastVerifiedToken.set(null);

            ossAuthEventPublisher.publishEventAsync(
                OssAuthEvent.builder()
                    .uid(basicAuthCredentials.getUid())
                    .iid(instanceService.fetch())
                    .date(Instant.now())
                    .ossAuth(
                        OssAuthEvent.OssAuth.builder()
                            .email(basicAuthCredentials.getUsername())
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

    public ConfiguredBasicAuth configuration() {
        var credentials = settingRepository.findByKey(BASIC_AUTH_SETTINGS_KEY)
            .map(Setting::getValue)
            .map(value -> JacksonMapper.ofJson(false).convertValue(value, SaltedBasicAuthCredentials.class))
            .orElse(null);
        return new ConfiguredBasicAuth(
            this.basicAuthConfiguration != null ? this.basicAuthConfiguration.realm : null,
            this.basicAuthConfiguration != null ? this.basicAuthConfiguration.openUrls : null,
            credentials
        );
    }

    public boolean isBasicAuthInitialized() {
        var configuration = configuration();

        return configuration.credentials() != null &&
            !StringUtils.isBlank(configuration.credentials().getUsername()) &&
            !StringUtils.isBlank(configuration.credentials().getPassword());
    }

    /**
     * Returns {@code true} if {@code currentPassword} matches the currently configured password.
     * Always re-checks against {@link #configuration()} directly, bypassing the {@link #isAuthenticated}
     * cache, so it cannot be satisfied by a credential already revoked elsewhere. Used to require
     * proof of the current password before {@link #save} is allowed to replace it.
     */
    public boolean validateCurrentPassword(String currentPassword) {
        SaltedBasicAuthCredentials credentials = configuration().credentials();
        if (credentials == null || currentPassword == null) {
            return false;
        }
        return AuthUtils.matches(credentials.getSalt(), currentPassword, credentials.getPassword());
    }

    /**
     * Returns {@code true} if the request carries valid basic-auth credentials
     * (either via the {@value BASIC_AUTH_COOKIE_NAME} cookie or an {@code Authorization: Basic} header).
     *
     * <p>bcrypt verification is only performed on a cache miss. Subsequent requests with the same
     * token, verified against unchanged credentials, hit the in-memory cache and pay only a
     * SHA-256 hash cost, keeping per-request latency negligible while the at-rest hash remains
     * bcrypt-strength.
     */
    public boolean isAuthenticated(HttpRequest<?> request) {
        SaltedBasicAuthCredentials credentials = configuration().credentials();
        if (credentials == null) {
            return false;
        }
        Optional<String> encoded = extractFromCookie(request).or(() -> extractFromAuthorizationHeader(request));
        if (encoded.isEmpty()) {
            return false;
        }
        try {
            String token = encoded.get();
            String tokenSha256 = sha256Hex(token);
            String credentialsFingerprint = credentialsFingerprint(credentials);

            // Fast path: same token verified against these exact credentials — skip bcrypt
            // entirely. Re-deriving the fingerprint from the freshly-fetched `credentials` above
            // on every call means a password change (on this node or a peer sharing the same
            // settings store) invalidates the cache on the very next request.
            // compare via MessageDigest.isEqual to prevent timing attacks
            CachedToken cached = lastVerifiedToken.get();
            if (cached != null
                && MessageDigest.isEqual(tokenSha256.getBytes(StandardCharsets.UTF_8), cached.tokenSha256().getBytes(StandardCharsets.UTF_8))
                && MessageDigest.isEqual(credentialsFingerprint.getBytes(StandardCharsets.UTF_8), cached.credentialsFingerprint().getBytes(StandardCharsets.UTF_8))) {
                return true;
            }

            String decoded = new String(Base64.getDecoder().decode(token));
            int colonIdx = decoded.indexOf(':');
            if (colonIdx < 0) {
                return false;
            }
            String username = decoded.substring(0, colonIdx);
            String password = decoded.substring(colonIdx + 1);

            boolean valid = username.equals(credentials.getUsername())
                && AuthUtils.matches(credentials.getSalt(), password, credentials.getPassword());

            // Wrong passwords always pay the full bcrypt cost; only cache successes.
            if (valid) {
                lastVerifiedToken.set(new CachedToken(tokenSha256, credentialsFingerprint));
            }
            return valid;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns a cache key identifying {@code credentials}, so a cached token can be told apart
     * from one verified against a since-replaced username/password.
     */
    private static String credentialsFingerprint(SaltedBasicAuthCredentials credentials) {
        return sha256Hex(credentials.getUsername() + ":" + credentials.getSalt() + ":" + credentials.getPassword());
    }

    private Optional<String> extractFromCookie(HttpRequest<?> request) {
        try {
            Cookie cookie = request.getCookies().get(BASIC_AUTH_COOKIE_NAME);
            if (cookie != null) {
                return Optional.of(cookie.getValue());
            }
        } catch (Exception ignored) {}
        // Fallback: parse the raw Cookie header directly (handles Micronaut's SimpleHttpRequest in unit tests).
        String raw = request.getHeaders().get("Cookie");
        if (raw == null) {
            return Optional.empty();
        }
        for (String pair : raw.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && BASIC_AUTH_COOKIE_NAME.equals(pair.substring(0, eq).trim())) {
                return Optional.of(pair.substring(eq + 1).trim());
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractFromAuthorizationHeader(HttpRequest<?> request) {
        return request.getHeaders()
            .getAuthorization()
            .filter(auth -> auth.toLowerCase().startsWith("basic"))
            .map(cred -> cred.substring("Basic ".length()));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    @ConfigurationProperties("kestra.server.basic-auth")
    @VisibleForTesting
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
            @Nullable List<String> openUrls) {
            this.username = username;
            this.password = password;
            this.realm = Optional.ofNullable(realm).orElse("Kestra");
            this.openUrls = Optional.ofNullable(openUrls).orElse(Collections.emptyList());
        }
    }

    public record ConfiguredBasicAuth(
        String realm,
        List<String> openUrls,
        SaltedBasicAuthCredentials credentials) {
    }

    @Getter
    @EqualsAndHashCode
    public static class SaltedBasicAuthCredentials {
        private String salt;
        private String username;
        protected String password;

        public SaltedBasicAuthCredentials(String salt, String username, String password) {
            Objects.requireNonNull(salt);
            Objects.requireNonNull(username);
            Objects.requireNonNull(password);
            this.salt = salt;
            this.username = username;
            this.password = password;
        }

        public static SaltedBasicAuthCredentials salt(String salt, String username, String password) {
            var salt1 = salt == null
                ? AuthUtils.generateSalt()
                : salt;
            return new SaltedBasicAuthCredentials(
                salt1,
                username,
                AuthUtils.hashPassword(salt1, password)
            );
        }
    }
}

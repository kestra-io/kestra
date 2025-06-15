package io.kestra.core.validations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class UrlValidatorTest {
    @Inject
    private ModelValidator modelValidator;

    @AllArgsConstructor
    @Introspected
    @Getter
    public static class SimpleUrlCls {
        @Url
        String url;
    }

    @AllArgsConstructor
    @Introspected
    @Getter
    public static class UrlWithHttpSchemeCls {
        @Url(scheme = "(http|https)")
        String url;
    }

    @Test
    void noSchemeInputValidation() {
        final SimpleUrlCls validUrl = new SimpleUrlCls("https://postgres-oss.preview.dev.kestra.io");

        assertThat(modelValidator.isValid(validUrl)).isEmpty();

        final SimpleUrlCls invalidUrl = new SimpleUrlCls("postgres-oss.preview.dev.kestra.io");

        assertThat(modelValidator.isValid(invalidUrl))
            .isPresent()
            .hasValueSatisfying(throwable ->
                assertThat(throwable)
                    .hasMessageContaining("invalid URL")
            );
    }

    @Test
    void validSchemeInputValidation() {
        final UrlWithHttpSchemeCls httpUrl = new UrlWithHttpSchemeCls("http://postgres-oss.preview.dev.kestra.io");

        assertThat(modelValidator.isValid(httpUrl)).isEmpty();

        final UrlWithHttpSchemeCls httpsUrl = new UrlWithHttpSchemeCls("https://postgres-oss.preview.dev.kestra.io");

        assertThat(modelValidator.isValid(httpsUrl)).isEmpty();

        final UrlWithHttpSchemeCls invalidSchemeUrl = new UrlWithHttpSchemeCls("ftp://postgres-oss.preview.dev.kestra.io");

        assertThat(modelValidator.isValid(invalidSchemeUrl))
            .isPresent()
            .hasValueSatisfying(throwable ->
                assertThat(throwable)
                    .hasMessageContaining("URL scheme doesn't match")
            );
    }
}

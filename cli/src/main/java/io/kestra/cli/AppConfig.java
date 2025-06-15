package io.kestra.cli;

import io.kestra.core.validations.Url;
import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Enforces various validation rules upon the application configuration preventing misconfiguration.
 */
@ConfigurationProperties("kestra")
public class AppConfig {

    @Url(
        scheme = "(http|https)",
        message = "invalid URL [{validatedValue}] - 'kestra.url' configuration property must be a valid HTTP(S) URL " +
                " - e.g. https://your.company.com"
    )
    public String url;
}

package org.kestra.storage.local;

import com.google.common.base.CharMatcher;
import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import javax.inject.Singleton;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

@Singleton
@Getter
@ConfigurationProperties("kestra.storage.local")
public class LocalConfig {
    String basePath;

    public URI getBasePath() {
        try {
            return new URI("file://" + CharMatcher.anyOf(File.separator).trimTrailingFrom(this.basePath));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
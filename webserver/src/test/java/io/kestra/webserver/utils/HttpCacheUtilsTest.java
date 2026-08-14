package io.kestra.webserver.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCacheUtilsTest {
    @Test
    void shouldNotAcceptWhenHeaderIsMissingOrBlank() {
        assertThat(HttpCacheUtils.accepts(null, "gzip")).isFalse();
        assertThat(HttpCacheUtils.accepts("", "gzip")).isFalse();
        assertThat(HttpCacheUtils.accepts("   ", "gzip")).isFalse();
    }

    @Test
    void shouldAcceptWhenEncodingIsListed() {
        assertThat(HttpCacheUtils.accepts("gzip", "gzip")).isTrue();
        assertThat(HttpCacheUtils.accepts("gzip, deflate, br, zstd", "gzip")).isTrue();
        assertThat(HttpCacheUtils.accepts("gzip, deflate, br, zstd", "br")).isTrue();
        assertThat(HttpCacheUtils.accepts("br;q=1.0, gzip;q=0.8", "gzip")).isTrue();
    }

    @Test
    void shouldNotAcceptWhenEncodingIsNotListed() {
        assertThat(HttpCacheUtils.accepts("deflate, zstd", "gzip")).isFalse();
        assertThat(HttpCacheUtils.accepts("gzip", "br")).isFalse();
    }

    @Test
    void shouldNotAcceptWhenQualityIsZero() {
        assertThat(HttpCacheUtils.accepts("gzip;q=0", "gzip")).isFalse();
        assertThat(HttpCacheUtils.accepts("gzip; q=0.0, br", "gzip")).isFalse();
        assertThat(HttpCacheUtils.accepts("gzip; q=0.0, br", "br")).isTrue();
    }

    @Test
    void shouldBeCaseInsensitiveForEncodingNames() {
        assertThat(HttpCacheUtils.accepts("GZIP", "gzip")).isTrue();
        assertThat(HttpCacheUtils.accepts("Br", "br")).isTrue();
    }

    @Test
    void shouldHonourWildcard() {
        assertThat(HttpCacheUtils.accepts("*", "gzip")).isTrue();
        assertThat(HttpCacheUtils.accepts("*;q=0", "gzip")).isFalse();
        // An explicit refusal wins over the wildcard.
        assertThat(HttpCacheUtils.accepts("*, gzip;q=0", "gzip")).isFalse();
        assertThat(HttpCacheUtils.accepts("*, gzip;q=0", "br")).isTrue();
    }

    @Test
    void shouldMatchEtagWhenListed() {
        assertThat(HttpCacheUtils.anyEtagMatches("\"abc\"", "\"abc\"")).isTrue();
        assertThat(HttpCacheUtils.anyEtagMatches("\"xyz\", \"abc\"", "\"abc\"")).isTrue();
        assertThat(HttpCacheUtils.anyEtagMatches("W/\"abc\"", "\"abc\"")).isTrue();
        assertThat(HttpCacheUtils.anyEtagMatches("*", "\"abc\"")).isTrue();
    }

    @Test
    void shouldNotMatchEtagWhenAbsentOrDifferent() {
        assertThat(HttpCacheUtils.anyEtagMatches(null, "\"abc\"")).isFalse();
        assertThat(HttpCacheUtils.anyEtagMatches("", "\"abc\"")).isFalse();
        assertThat(HttpCacheUtils.anyEtagMatches("\"xyz\"", "\"abc\"")).isFalse();
    }
}

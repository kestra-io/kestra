package io.kestra.webserver.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCacheUtilsTest {
    @Test
    void shouldMatchEtagWhenListed() {
        assertThat(HttpCacheUtils.anyEtagMatches("\"abc\"", "\"abc\"")).isTrue();
        assertThat(HttpCacheUtils.anyEtagMatches("\"xyz\", \"abc\"", "\"abc\"")).isTrue();
        assertThat(HttpCacheUtils.anyEtagMatches("W/\"abc\"", "\"abc\"")).isTrue();
        assertThat(HttpCacheUtils.anyEtagMatches("W/\"abc\"", "W/\"abc\"")).isTrue();
        assertThat(HttpCacheUtils.anyEtagMatches("\"abc\"", "W/\"abc\"")).isTrue();
        assertThat(HttpCacheUtils.anyEtagMatches("*", "\"abc\"")).isTrue();
    }

    @Test
    void shouldNotMatchEtagWhenAbsentOrDifferent() {
        assertThat(HttpCacheUtils.anyEtagMatches(null, "\"abc\"")).isFalse();
        assertThat(HttpCacheUtils.anyEtagMatches("", "\"abc\"")).isFalse();
        assertThat(HttpCacheUtils.anyEtagMatches("\"xyz\"", "\"abc\"")).isFalse();
    }

    @Test
    void shouldDeriveAStrongEtagFromTheContentDigest() {
        assertThat(HttpCacheUtils.etag(HttpCacheUtils.sha256Hex("kestra".getBytes())))
            .isEqualTo("\"" + HttpCacheUtils.sha256Hex("kestra".getBytes()) + "\"")
            .isNotEqualTo(HttpCacheUtils.etag(HttpCacheUtils.sha256Hex("kestrb".getBytes())));
    }
}

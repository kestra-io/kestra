package io.kestra.core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTest {

    @Test
    void shouldGetExtension() {
        assertThat(FileUtils.getExtension((String) null)).isNull();
        assertThat(FileUtils.getExtension("")).isNull();
        assertThat(FileUtils.getExtension("/file/hello")).isNull();
        assertThat(FileUtils.getExtension("/file/hello.txt")).isEqualTo(".txt");
        assertThat(FileUtils.getExtension("/file/hello.file with spaces.txt")).isEqualTo(".txt");
        assertThat(FileUtils.getExtension("/file/hello.file.with.multiple.dots.txt")).isEqualTo(".txt");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "kestra:///ns/flow/exec/abc/output",
        "kestra:///ns/flow/exec/abc/.output",
    })
    void isParentTraversal_false(String path) {
        assertThat(FileUtils.isParentTraversal(URI.create(path))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "kestra:///ns/flow/exec/abc/../../../etc/passwd",
        "kestra:///ns/flow/exec/abc/%2E%2E/%2E%2E/%2E%2E/etc/passwd",
        "kestra:///ns/flow/exec/abc/%2e%2e/%2e%2e/%2e%2e/etc/passwd",
        "kestra:///ns/flow/exec/abc%2F%2E%2E%2Fetc%2Fpasswd"
    })
    void isParentTraversal_true(String path) {
        assertThat(FileUtils.isParentTraversal(URI.create(path))).isTrue();
    }
}
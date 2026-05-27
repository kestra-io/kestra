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
        // a filename that merely contains ".." is not a traversal segment
        "kestra:///ns/flow/exec/abc/my..file.txt",
    })
    void isParentTraversal_false(String path) {
        assertThat(FileUtils.isParentTraversal(URI.create(path))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "kestra:///ns/flow/exec/abc/../../../etc/passwd",
        "kestra:///ns/flow/exec/abc/%2E%2E/%2E%2E/%2E%2E/etc/passwd",
        "kestra:///ns/flow/exec/abc/%2e%2e/%2e%2e/%2e%2e/etc/passwd",
        "kestra:///ns/flow/exec/abc%2F%2E%2E%2Fetc%2Fpasswd",
        // GHSA-qw4v-6w32-xx9h: Windows-style backslash traversal must also be rejected.
        // %5C decodes to '\' in URI.getPath().
        "kestra:///ns/flow/exec/abc%5C..%5C..%5C..%5Cetc%5Cpasswd",
        "kestra:///ns/flow/exec/abc%5C%2E%2E%5C%2E%2E%5Cetc%5Cpasswd",
        // mixed separators
        "kestra:///ns/flow/exec/abc/..%5C..%5Cetc%5Cpasswd",
    })
    void isParentTraversal_true(String path) {
        assertThat(FileUtils.isParentTraversal(URI.create(path))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "..",
        "../etc/passwd",
        "ns/exec/../../etc/passwd",
        "ns/exec/output/..",
        // GHSA-qw4v-6w32-xx9h: backslash variants (already decoded, as from URI.getPath())
        "..\\" + "etc\\passwd",
        "ns\\exec\\..\\.." + "\\etc\\passwd",
        "ns/exec/abc\\..\\..\\.." + "\\etc\\passwd",
    })
    void isParentTraversalString_true(String path) {
        assertThat(FileUtils.isParentTraversal(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "ns/flow/exec/abc/output",
        "ns/flow/exec/abc/.output",
        "ns/flow/exec/abc/my..file.txt",
    })
    void isParentTraversalString_false(String path) {
        assertThat(FileUtils.isParentTraversal(path)).isFalse();
    }

    @Test
    void isParentTraversal_handlesNull() {
        assertThat(FileUtils.isParentTraversal((URI) null)).isFalse();
        assertThat(FileUtils.isParentTraversal((String) null)).isFalse();
    }
}

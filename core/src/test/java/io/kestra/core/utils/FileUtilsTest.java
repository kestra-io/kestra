package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

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
}
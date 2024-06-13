package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class FileUtilsTest {

    @Test
    void shouldGetExtension() {
        assertThat(FileUtils.getExtension((String)null), nullValue());
        assertThat(FileUtils.getExtension(""), nullValue());
        assertThat(FileUtils.getExtension("/file/hello"), nullValue());
        assertThat(FileUtils.getExtension("/file/hello.txt"), is(".txt"));
    }
}
package io.kestra.core.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class IdUtilsTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "kestra.io",
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi eu egestas ligula. Fusce molestie egestas sodales. Morbi ullamcorper nisi sit amet fringilla aliquet. Sed vitae lectus gravida, congue arcu id, accumsan elit. Cras ultrices neque non ex gravida, id faucibus enim aliquet. Phasellus sodales euismod dui non semper. Proin efficitur ac nisi vitae vestibulum. Nullam consequat lectus dui, vulputate molestie turpis commodo quis. Suspendisse sit amet odio facilisis, gravida purus quis, convallis mi. Sed dignissim mi nec sem molestie sagittis. Quisque sollicitudin sed ex ut laoreet. Nunc a bibendum lectus. Fusce volutpat pharetra risus eu auctor.",
    })
    void merge(String from) {
        String convert = IdUtils.from(from);
        assertThat(convert, is(IdUtils.from(from)));
    }
}
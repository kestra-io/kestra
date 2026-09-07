package io.kestra.core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IdUtilsTest {
    @ParameterizedTest
    @ValueSource(
        strings = {
            "kestra.io",
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi eu egestas ligula. Fusce molestie egestas sodales. Morbi ullamcorper nisi sit amet fringilla aliquet. Sed vitae lectus gravida, congue arcu id, accumsan elit. Cras ultrices neque non ex gravida, id faucibus enim aliquet. Phasellus sodales euismod dui non semper. Proin efficitur ac nisi vitae vestibulum. Nullam consequat lectus dui, vulputate molestie turpis commodo quis. Suspendisse sit amet odio facilisis, gravida purus quis, convallis mi. Sed dignissim mi nec sem molestie sagittis. Quisque sollicitudin sed ex ut laoreet. Nunc a bibendum lectus. Fusce volutpat pharetra risus eu auctor.",
        }
    )
    void from(String from) {
        String convert = IdUtils.from(from);
        assertThat(convert).isEqualTo(IdUtils.from(from));
    }

    @Test
    void create() {
        String id = IdUtils.create();
        assertThat(id).isNotNull();
    }

    @Test
    void fromParts() {
        String id = IdUtils.fromParts("namespace", "flow");
        assertThat(id).isNotNull();
        assertThat(id).isEqualTo("9:namespace4:flow");

        String idWithNull = IdUtils.fromParts(null, "namespace", "flow");
        assertThat(idWithNull).isNotNull();
        assertThat(idWithNull).isEqualTo("9:namespace4:flow");
    }

    @Test
    void shouldNotCollidWhenPartsContainSeparator() {
        assertThat(IdUtils.fromParts("team", "x_y"))
            .isNotEqualTo(IdUtils.fromParts("team_x", "y"));
    }

    @Test
    void shouldSkipNullPartsIdentically() {
        assertThat(IdUtils.fromParts(null, "namespace", "flow"))
            .isEqualTo(IdUtils.fromParts("namespace", "flow"));

        assertThat(IdUtils.fromParts("tenant", null, "namespace"))
            .isEqualTo(IdUtils.fromParts("tenant", "namespace"));

        assertThat(IdUtils.fromParts(null, null, "only"))
            .isEqualTo(IdUtils.fromParts("only"));
    }

    @Test
    void fromPartsAndSeparator() {
        String id = IdUtils.fromPartsAndSeparator('|', "namespace", "flow");
        assertThat(id).isNotNull();
        assertThat(id).isEqualTo("namespace|flow");

        String idWithNull = IdUtils.fromPartsAndSeparator('|', null, "namespace", "flow");
        assertThat(idWithNull).isNotNull();
        assertThat(idWithNull).isEqualTo("namespace|flow");
    }
}
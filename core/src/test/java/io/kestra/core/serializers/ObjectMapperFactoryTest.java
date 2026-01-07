package io.kestra.core.serializers;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.junit.annotations.KestraTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class ObjectMapperFactoryTest {
    @Inject
    ObjectMapper objectMapper;

    @Data
    @NoArgsConstructor
    @JsonPropertyOrder(alphabetic = true)
    public static class Bean {
        private int intNull;
        private int intDefault = 0;
        private int intChange = 0;

        private Integer integerNull;
        private Integer integerDefault = 0;
        private Integer integerChange = 0;

        private boolean boolNull;
        private boolean boolDefaultTrue = true;
        private boolean boolChangeTrue = true;
        private boolean boolDefaultFalse = false;
        private boolean boolChangeFalse = false;

        private Boolean booleanNull;
        private Boolean booleanDefaultTrue = true;
        private Boolean booleanChangeTrue = true;
        private Boolean booleanDefaultFalse = false;
        private Boolean booleanChangeFalse = false;

        private String stringNull;
        private String stringDefault = "bla";
        private String stringChange = "bla";

        private Duration duration;
        private ZonedDateTime zonedDateTime;
    }

    @Test
    void serialize() throws JsonProcessingException {
        Bean b = new Bean();

        b.setIntChange(1);
        b.setIntegerChange(1);
        b.setBoolChangeTrue(false);
        b.setBoolChangeFalse(true);
        b.setBooleanChangeTrue(false);
        b.setBooleanChangeFalse(true);
        b.setStringChange("foo");

        b.setDuration(Duration.parse("PT5M"));
        b.setZonedDateTime(ZonedDateTime.parse("2013-09-08T16:19:12.000000+02:00"));

        String s = objectMapper.writeValueAsString(b);

        assertThat(s).contains("\"intNull\":0");
        assertThat(s).contains("\"intDefault\":0");
        assertThat(s).contains("\"intChange\":1");

        assertThat(s).doesNotContain("\"integerNull\":");
        assertThat(s).contains("\"integerDefault\":0");
        assertThat(s).contains("\"integerChange\":1");

        assertThat(s).contains("\"boolNull\":false");
        assertThat(s).contains("\"boolDefaultTrue\":true");
        assertThat(s).contains("\"boolChangeTrue\":false");
        assertThat(s).contains("\"boolDefaultFalse\":false");
        assertThat(s).contains("\"boolChangeTrue\":false");

        assertThat(s).doesNotContain("\"booleanNull\":");
        assertThat(s).contains("\"booleanDefaultTrue\":true");
        assertThat(s).contains("\"booleanChangeTrue\":false");
        assertThat(s).contains("\"booleanDefaultFalse\":false");
        assertThat(s).contains("\"booleanChangeTrue\":false");

        assertThat(s).doesNotContain("\"stringNull\":");
        assertThat(s).contains("\"stringDefault\":\"bla\"");
        assertThat(s).contains("\"stringChange\":\"foo\"");

        assertThat(s).contains("\"duration\":\"PT5M\"");
        assertThat(s).contains("\"zonedDateTime\":\"2013-09-08T16:19:12+02:00\"");
    }


    @Test
    void deserialize() throws JsonProcessingException {
        Bean bean = objectMapper.readValue(
            "{\"boolChangeFalse\":true,\"boolChangeTrue\":false,\"booleanChangeFalse\":true,\"booleanChangeTrue\":false,\"duration\":\"PT5M\",\"intChange\":1,\"integerChange\":1,\"stringChange\":\"foo\",\"zonedDateTime\":\"2013-09-08T16:19:12+02:00\"}",
            Bean.class
        );

        assertThat(bean.intNull).isZero();
        assertThat(bean.intDefault).isZero();
        assertThat(bean.intChange).isEqualTo(1);

        assertThat(bean.integerNull).isNull();
        assertThat(bean.integerDefault).isZero();
        assertThat(bean.integerChange).isEqualTo(1);

        assertThat(bean.boolNull).isFalse();
        assertThat(bean.boolDefaultTrue).isTrue();
        assertThat(bean.boolChangeTrue).isFalse();
        assertThat(bean.boolDefaultFalse).isFalse();
        assertThat(bean.boolChangeFalse).isTrue();

        assertThat(bean.booleanNull).isNull();
        assertThat(bean.booleanDefaultTrue).isTrue();
        assertThat(bean.booleanChangeTrue).isFalse();
        assertThat(bean.booleanDefaultFalse).isFalse();
        assertThat(bean.booleanChangeFalse).isTrue();

        assertThat(bean.stringNull).isNull();
        assertThat(bean.stringDefault).isEqualTo("bla");
        assertThat(bean.stringChange).isEqualTo("foo");

        assertThat(bean.duration).isEqualTo(Duration.parse("PT5M"));
    }
}
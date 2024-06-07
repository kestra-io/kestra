package io.kestra.core.serializers;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.junit.annotations.KestraTest;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
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

        assertThat(s, containsString("\"intNull\":0"));
        assertThat(s, containsString("\"intDefault\":0"));
        assertThat(s, containsString("\"intChange\":1"));

        assertThat(s, not(containsString("\"integerNull\":")));
        assertThat(s, containsString("\"integerDefault\":0"));
        assertThat(s, containsString("\"integerChange\":1"));

        assertThat(s, containsString("\"boolNull\":false"));
        assertThat(s, containsString("\"boolDefaultTrue\":true"));
        assertThat(s, containsString("\"boolChangeTrue\":false"));
        assertThat(s, containsString("\"boolDefaultFalse\":false"));
        assertThat(s, containsString("\"boolChangeTrue\":false"));

        assertThat(s, not(containsString("\"booleanNull\":")));
        assertThat(s, containsString("\"booleanDefaultTrue\":true"));
        assertThat(s, containsString("\"booleanChangeTrue\":false"));
        assertThat(s, containsString("\"booleanDefaultFalse\":false"));
        assertThat(s, containsString("\"booleanChangeTrue\":false"));

        assertThat(s, not(containsString("\"stringNull\":")));
        assertThat(s, containsString("\"stringDefault\":\"bla\""));
        assertThat(s, containsString("\"stringChange\":\"foo\""));

        assertThat(s, containsString("\"duration\":\"PT5M\""));
        assertThat(s, containsString("\"zonedDateTime\":\"2013-09-08T16:19:12+02:00\""));
    }


    @Test
    void deserialize() throws JsonProcessingException {
        Bean bean = objectMapper.readValue(
            "{\"boolChangeFalse\":true,\"boolChangeTrue\":false,\"booleanChangeFalse\":true,\"booleanChangeTrue\":false,\"duration\":\"PT5M\",\"intChange\":1,\"integerChange\":1,\"stringChange\":\"foo\",\"zonedDateTime\":\"2013-09-08T16:19:12+02:00\"}",
            Bean.class
        );

        assertThat(bean.intNull, is(0));
        assertThat(bean.intDefault, is(0));
        assertThat(bean.intChange, is(1));

        assertThat(bean.integerNull, is(nullValue()));
        assertThat(bean.integerDefault, is(0));
        assertThat(bean.integerChange, is(1));

        assertThat(bean.boolNull, is(false));
        assertThat(bean.boolDefaultTrue, is(true));
        assertThat(bean.boolChangeTrue, is(false));
        assertThat(bean.boolDefaultFalse, is(false));
        assertThat(bean.boolChangeFalse, is(true));

        assertThat(bean.booleanNull, is(nullValue()));
        assertThat(bean.booleanDefaultTrue, is(true));
        assertThat(bean.booleanChangeTrue, is(false));
        assertThat(bean.booleanDefaultFalse, is(false));
        assertThat(bean.booleanChangeFalse, is(true));

        assertThat(bean.stringNull, is(nullValue()));
        assertThat(bean.stringDefault, is("bla"));
        assertThat(bean.stringChange, is("foo"));

        assertThat(bean.duration, is(Duration.parse("PT5M")));
    }
}
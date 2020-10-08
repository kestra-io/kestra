package org.kestra.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class JacksonMapperTest {
    private TimeZone timeZone;

    @BeforeEach
    void init() {
        timeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(timeZone);
    }

    @Test
    void parse() throws IOException {
        ObjectMapper mapper = JacksonMapper.ofJson();

        Pojo original = new Pojo(
            "test",
            Instant.parse("2013-09-08T16:19:12Z"),
            ZonedDateTime.parse("2013-09-08T16:19:12+03:00")
        );
        String s = mapper.writeValueAsString(original);
        Pojo deserialize = mapper.readValue(s, Pojo.class);

        assertThat(deserialize.getString(), is(original.getString()));
        assertThat(deserialize.getInstant().toEpochMilli(), is(original.getInstant().toEpochMilli()));
        assertThat(deserialize.getInstant().toString(), is(original.getInstant().toString()));
        assertThat(deserialize.getZonedDateTime().toEpochSecond(), is(original.getZonedDateTime().toEpochSecond()));
        assertThat(deserialize.getZonedDateTime().getOffset(), is(original.getZonedDateTime().getOffset()));
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pojo {
        private String string;
        private Instant instant;
        private ZonedDateTime zonedDateTime;
    }
}

package org.kestra.task.notifications.slack;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.runners.RunContext;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Objects;

@MicronautTest
class SlackIncomingWebhookTest {
    @Inject
    private ApplicationContext applicationContext;

    @Test
    void run() throws Exception {
        RunContext runContext = new RunContext(this.applicationContext, ImmutableMap.of(
            "blocks", Arrays.asList(
                ImmutableMap.of(
                    "text", "A message *with some bold text* and _some italicized text_.",
                    "fields", Arrays.asList("*Priority*", "*Type*", "`High`", "`Unit Test`")
                ),
                ImmutableMap.of(
                    "text", "his is a mrkdwn section block :ghost: *this is bold*, and ~this is crossed out~, and <https://google.com|this is a link>",
                    "fields", Arrays.asList("*Priority*", "*Type*", "`Low`", "`Unit Test`")
                )
            )
        ));

        SlackIncomingWebhook task = SlackIncomingWebhook.builder()
            .url("http://www.mocky.io/v2/5dfa3bfd3600007dafbd6b91")
            .payload(
                Files.asCharSource(
                    new File(Objects.requireNonNull(SlackIncomingWebhookTest.class.getClassLoader()
                        .getResource("slack.hbs"))
                        .toURI()),
                    Charsets.UTF_8
                ).read()
            )
            .build();

        task.run(runContext);
    }
}
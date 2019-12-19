package org.floworc.task.notifications.slack;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.DefaultHttpClient;
import io.micronaut.http.client.RxHttpClient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;

import java.net.URL;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class SlackIncomingWebhook extends Task implements RunnableTask {
    private String url;

    protected String payload;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        RxHttpClient client = new DefaultHttpClient(new URL(url));
        String payload = runContext.render(this.payload);

        runContext.logger(this.getClass()).debug("Send slack webhook: {}", payload);

        client.toBlocking().retrieve(HttpRequest.POST(url, payload));

        return null;
    }
}
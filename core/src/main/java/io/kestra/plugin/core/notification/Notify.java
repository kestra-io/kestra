package io.kestra.plugin.core.notification;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.notification.NotificationService;
import io.kestra.core.notification.model.Notification;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send an in-app notification to Kestra users.",
    description = """
        Each listed user gets an unread notification in the Kestra UI. Users are named by their username and must have access to the tenant of the flow, otherwise the task fails.

        In the open-source edition there is a single implicit user, who receives the notification whatever the list holds."""
)
@Plugin(
    examples = {
        @Example(
            title = "Notify the on-call team when a flow ends",
            full = true,
            code = """
                id: notify_on_call
                namespace: company.team

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World!

                  - id: notify
                    type: io.kestra.plugin.core.notification.Notify
                    users:
                      - alice@example.com
                      - bob@example.com
                    title: "{{ flow.id }} finished, execution {{ execution.id }}"
                    url: "/main/executions/{{ flow.namespace }}/{{ flow.id }}/{{ execution.id }}"
                """
        )
    }
)
public class Notify extends Task implements RunnableTask<Notify.Output>, SystemTask {
    @Schema(title = "The usernames of the users to notify.")
    @NotNull
    private Property<List<String>> users;

    @Schema(title = "The notification title shown to the users.")
    @NotNull
    private Property<String> title;

    @Schema(
        title = "The page opened when the notification is clicked.",
        description = "Either a path of the Kestra UI starting with `/`, without the `/ui` prefix (e.g. `/main/executions`), opened in the same tab, or an absolute `http` or `https` URL, shown as a link."
    )
    private Property<String> url;

    @Schema(
        title = "Whether the notification is created already read.",
        description = "A read notification shows in the users' notification list without raising the unread badge."
    )
    @Builder.Default
    private Property<Boolean> read = Property.ofValue(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> renderedUsers = runContext.render(users).asList(String.class).stream().distinct().toList();
        if (renderedUsers.isEmpty()) {
            throw new IllegalArgumentException("The 'users' property must name at least one user.");
        }
        String renderedTitle = runContext.render(title).as(String.class).orElseThrow();
        String renderedUrl = runContext.render(url).as(String.class).map(Notify::validateUrl).orElse(null);
        boolean renderedRead = runContext.render(read).as(Boolean.class).orElse(false);

        NotificationService notificationService = ((DefaultRunContext) runContext).services().additionalService(NotificationService.class);
        List<Notification> notifications = notificationService.notifyUsers(runContext.flowInfo().tenantId(), renderedUsers, renderedTitle, renderedUrl, renderedRead);

        return Output.builder().count(notifications.size()).build();
    }

    private static String validateUrl(String value) {
        if (value.startsWith("/") && !value.startsWith("//")) {
            return value;
        }

        try {
            URI uri = new URI(value);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null) {
                return value;
            }
        } catch (URISyntaxException e) {
            // reported below with the same message
        }

        throw new IllegalArgumentException(
            "The 'url' property must be a path starting with '/' or an absolute http or https URL, but was '%s'.".formatted(value)
        );
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The number of notifications sent.")
        private Integer count;
    }
}

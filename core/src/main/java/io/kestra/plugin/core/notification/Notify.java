package io.kestra.plugin.core.notification;

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
        boolean renderedRead = runContext.render(read).as(Boolean.class).orElse(false);

        NotificationService notificationService = ((DefaultRunContext) runContext).services().additionalService(NotificationService.class);
        List<Notification> notifications = notificationService.notifyUsers(runContext.flowInfo().tenantId(), renderedUsers, renderedTitle, renderedRead);

        return Output.builder().count(notifications.size()).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The number of notifications sent.")
        private Integer count;
    }
}

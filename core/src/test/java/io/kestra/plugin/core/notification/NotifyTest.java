package io.kestra.plugin.core.notification;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.notification.NotificationService;
import io.kestra.core.notification.model.Notification;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true, startSystemWorker = true)
class NotifyTest {
    @Inject
    private NotificationService notificationService;

    @ExecuteFlow("flows/valids/notify-users.yaml")
    @Test
    void shouldNotifyTheImplicitUserOnceWhenRunInOpenSource(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // OSS has a single implicit user, so the two listed users collapse into one unread notification for it
        assertThat(notificationsTitled("Done: " + execution.getId()))
            .singleElement()
            .extracting(Notification::isRead)
            .isEqualTo(false);
    }

    @ExecuteFlow("flows/valids/notify-users-read.yaml")
    @Test
    void shouldCreateAReadNotificationWhenReadIsSet(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(notificationsTitled("Done quietly: " + execution.getId()))
            .singleElement()
            .extracting(Notification::isRead)
            .isEqualTo(true);
    }

    private List<Notification> notificationsTitled(String title) {
        return notificationService.findByUser(null, null, 100).stream()
            .filter(notification -> title.equals(notification.getTitle()))
            .toList();
    }
}

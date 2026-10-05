package io.kestra.webserver.controllers.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.notification.NotificationRepositoryInterface;
import io.kestra.core.notification.NotificationService;
import io.kestra.core.notification.model.CoreNotificationType;
import io.kestra.core.notification.model.Notification;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.controllers.api.NotificationController.ApiMarkAllRead;
import io.kestra.webserver.controllers.api.NotificationController.ApiNotificationHistory;
import io.kestra.webserver.controllers.api.NotificationController.ApiNotificationsSince;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.sse.Event;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.reactor.http.client.ReactorSseClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import reactor.core.Disposable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OSS resolves no real user ({@link NotificationController#resolveCurrentUserId()} defaults to
 * {@code null}) — every notification created here belongs to that same {@code null} "user", and
 * there is no cross-user/cross-tenant visibility to exercise.
 */
@MicronautTest(environments = { "test", "h2" }, transactional = false)
public class NotificationControllerTest {

    private static final String API_V1_NOTIFICATIONS = "/api/v1/notifications";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    @Client("/")
    ReactorSseClient sseClient;

    @Inject
    private NotificationRepositoryInterface notificationRepository;

    @Inject
    private NotificationService notificationService;

    /**
     * OSS's shared {@code null} "user" means every test in this class shares the same
     * notifications; without this, count- and order-sensitive assertions (unread count, history
     * pagination) would depend on execution order.
     */
    @BeforeEach
    void cleanUpNotifications() {
        notificationRepository.deleteByQuery(Instant.now(), Instant.now().plusSeconds(60));
    }

    private Notification notification(Instant createdDate, boolean read) {
        return notificationRepository.create(
            Notification.builder()
                .id(IdUtils.create())
                .userId(null)
                .type(CoreNotificationType.GENERIC.name())
                .title("title")
                .read(read)
                .createdDate(createdDate)
                .updatedDate(createdDate)
                .build()
        );
    }

    @Test
    void shouldPollSinceReturnOnlyNotificationsUpdatedAfterGivenInstant() {
        Instant since = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        notification(since.minusSeconds(10), false);
        Notification after = notification(since.plusSeconds(10), false);

        var request = HttpRequest.GET(API_V1_NOTIFICATIONS + "/since?since=" + since).contentType(MediaType.APPLICATION_JSON);
        ApiNotificationsSince response = client.toBlocking().retrieve(request, ApiNotificationsSince.class);

        assertThat(response.notifications()).extracting(Notification::getId).containsExactly(after.getId());
    }

    @Test
    void shouldFollowLiveNotificationCreatedForCurrentUser() throws Exception {
        HttpRequest<?> request = HttpRequest.GET(API_V1_NOTIFICATIONS + "/follow").accept(MediaType.TEXT_EVENT_STREAM_TYPE);

        // OSS's shared null "user" means the broadcast queue may still be delivering another
        // test's backlog when this subscription opens; filter for our own notification's id
        // instead of assuming the first delivered event is ours.
        AtomicReference<String> expectedId = new AtomicReference<>();
        CompletableFuture<Event<Notification>> future = new CompletableFuture<>();
        Disposable subscription = sseClient.eventStream(request, Notification.class)
            .filter(event -> event.getData() != null && event.getData().getId().equals(expectedId.get()))
            .subscribe(future::complete, future::completeExceptionally);

        try {
            // give the SSE connection time to register server-side before the notification is created.
            // Goes through NotificationService (not the repository directly) since that's the seam that emits NotificationEvents.
            Thread.sleep(300);
            Notification created = notificationService.notify(null, null, CoreNotificationType.GENERIC, "title", null);
            expectedId.set(created.getId());

            Event<Notification> received = future.get(5, TimeUnit.SECONDS);
            assertThat(received.getId()).isEqualTo("created");
            assertThat(received.getData().getId()).isEqualTo(created.getId());
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void shouldReturnHistoryPaginatedByCursorNewestFirst() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Notification n1 = notification(base.plusSeconds(1), false);
        Notification n2 = notification(base.plusSeconds(2), false);
        Notification n3 = notification(base.plusSeconds(3), false);

        var page1Request = HttpRequest.GET(API_V1_NOTIFICATIONS + "/history?limit=2").contentType(MediaType.APPLICATION_JSON);
        ApiNotificationHistory page1 = client.toBlocking().retrieve(page1Request, ApiNotificationHistory.class);

        assertThat(page1.notifications()).extracting(Notification::getId).containsExactly(n3.getId(), n2.getId());
        assertThat(page1.nextCursor()).isNotNull();

        var page2Request = HttpRequest.GET(API_V1_NOTIFICATIONS + "/history?limit=2&before=" + URLEncoder.encode(page1.nextCursor(), StandardCharsets.UTF_8))
            .contentType(MediaType.APPLICATION_JSON);
        ApiNotificationHistory page2 = client.toBlocking().retrieve(page2Request, ApiNotificationHistory.class);

        assertThat(page2.notifications()).extracting(Notification::getId).containsExactly(n1.getId());
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    void shouldReturnUnreadCount() {
        notification(Instant.now(), false);
        notification(Instant.now(), true);

        var request = HttpRequest.GET(API_V1_NOTIFICATIONS + "/unread-count").contentType(MediaType.APPLICATION_JSON);
        Long response = client.toBlocking().retrieve(request, Long.class);

        assertThat(response).isEqualTo(1L);
    }

    @Test
    void shouldMarkNotificationAsReadGivenValidId() {
        Notification created = notification(Instant.now(), false);

        var request = HttpRequest.POST(API_V1_NOTIFICATIONS + "/" + created.getId() + "/read", null).contentType(MediaType.APPLICATION_JSON);
        HttpResponse<Object> response = client.toBlocking().exchange(request);

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
        assertThat(notificationRepository.findById(null, created.getId()).orElseThrow().isRead()).isTrue();
    }

    @Test
    void shouldMarkNotificationAsUnreadGivenValidId() {
        Notification created = notification(Instant.now(), true);

        var request = HttpRequest.POST(API_V1_NOTIFICATIONS + "/" + created.getId() + "/unread", null).contentType(MediaType.APPLICATION_JSON);
        HttpResponse<Object> response = client.toBlocking().exchange(request);

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
        assertThat(notificationRepository.findById(null, created.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void shouldReturnNoContentForMarkReadGivenUnknownIdIdempotently() {
        // Mark-read is idempotent: an unknown/foreign/already-purged id is a no-op success, not a
        // 404 — a 404 here would trip the frontend's global error-page interceptor for what is,
        // from the caller's perspective, an already-satisfied request.
        HttpResponse<Object> response = client.toBlocking().exchange(
            HttpRequest.POST(API_V1_NOTIFICATIONS + "/unknown/read", null).contentType(MediaType.APPLICATION_JSON)
        );

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
    }

    @Test
    void shouldMarkAllRead() {
        Notification created = notification(Instant.now(), false);

        var request = HttpRequest.POST(API_V1_NOTIFICATIONS + "/read-all", null).contentType(MediaType.APPLICATION_JSON);
        ApiMarkAllRead response = client.toBlocking().retrieve(request, ApiMarkAllRead.class);

        assertThat(response.updated()).isEqualTo(1);
        assertThat(notificationRepository.findById(null, created.getId()).orElseThrow().isRead()).isTrue();
    }
}

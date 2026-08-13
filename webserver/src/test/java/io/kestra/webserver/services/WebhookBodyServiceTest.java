package io.kestra.webserver.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.HttpRequest.ByteArrayRequestBody;
import io.kestra.core.http.HttpRequest.MultipartFormDataRequestBody;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.storages.StorageInterface;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger.FetchType;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.server.multipart.MultipartBody;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebhookBodyServiceTest {
    private static final Flow FLOW = Flow.builder()
        .tenantId("main")
        .id("webhook")
        .namespace("io.kestra.tests")
        .build();

    private static final String EXECUTION_ID = "4Xh0eZFtGKAmQrjjs2fUUZ";

    @Test
    void shouldStoreFilePartUnderTheExecutionAndKeepFormFieldContent() throws IOException {
        // Given
        StorageInterface storage = storage();
        WebhookBodyService service = new WebhookBodyService(storage);
        MultipartBody body = body(
            fileUpload("photo", "result.jpg", MediaType.IMAGE_JPEG, "binary".getBytes(StandardCharsets.UTF_8)),
            formField("note", "looks good")
        );

        // When
        List<MultipartFormDataRequestBody.Part> parts = service
            .collect(body, FLOW, EXECUTION_ID, MediaType.MULTIPART_FORM_DATA)
            .block()
            .getContent();

        // Then
        assertThat(parts).hasSize(2);
        assertThat(parts.getFirst()).isEqualTo(new MultipartFormDataRequestBody.FilePart(
            "photo",
            "result.jpg",
            MediaType.IMAGE_JPEG,
            6L,
            URI.create("kestra:///io/kestra/tests/webhook/executions/" + EXECUTION_ID + "/webhook/0/result.jpg")
        ));
        assertThat(parts.getLast()).isInstanceOf(MultipartFormDataRequestBody.FormFieldPart.class);
        assertThat(new String(((MultipartFormDataRequestBody.FormFieldPart) parts.getLast()).content(), StandardCharsets.UTF_8))
            .isEqualTo("looks good");
    }

    @Test
    void shouldStoreFilePartUnderTheExecutionWhenItsFilenameTraversesItsDirectory() throws IOException {
        // Given
        StorageInterface storage = storage();
        WebhookBodyService service = new WebhookBodyService(storage);
        MultipartBody body = body(fileUpload("photo", "../../evil.jpg", MediaType.IMAGE_JPEG, new byte[] { 1 }));

        // When
        List<MultipartFormDataRequestBody.Part> parts = service
            .collect(body, FLOW, EXECUTION_ID, MediaType.MULTIPART_FORM_DATA)
            .block()
            .getContent();

        // Then — only the file name is kept, so the part cannot be written outside the execution directory
        assertThat(((MultipartFormDataRequestBody.FilePart) parts.getFirst()).uri())
            .isEqualTo(URI.create("kestra:///io/kestra/tests/webhook/executions/" + EXECUTION_ID + "/webhook/0/evil.jpg"));
    }

    @Test
    void shouldNumberPartsWhenTheyShareTheirFilename() throws IOException {
        // Given
        StorageInterface storage = storage();
        WebhookBodyService service = new WebhookBodyService(storage);
        MultipartBody body = body(
            fileUpload("photo", "result.jpg", MediaType.IMAGE_JPEG, new byte[] { 1 }),
            fileUpload("photo", "result.jpg", MediaType.IMAGE_JPEG, new byte[] { 2 })
        );

        // When
        List<MultipartFormDataRequestBody.Part> parts = service
            .collect(body, FLOW, EXECUTION_ID, MediaType.MULTIPART_FORM_DATA)
            .block()
            .getContent();

        // Then
        assertThat(parts)
            .extracting(part -> ((MultipartFormDataRequestBody.FilePart) part).uri().getPath())
            .containsExactly(
                "/io/kestra/tests/webhook/executions/" + EXECUTION_ID + "/webhook/0/result.jpg",
                "/io/kestra/tests/webhook/executions/" + EXECUTION_ID + "/webhook/1/result.jpg"
            );
    }

    @Test
    void shouldReturnEmptyBodyWhenRequestHasNoPart() {
        // Given
        WebhookBodyService service = new WebhookBodyService(mock(StorageInterface.class));

        // When
        MultipartFormDataRequestBody body = service
            .collect(null, FLOW, EXECUTION_ID, MediaType.MULTIPART_FORM_DATA)
            .block();

        // Then
        assertThat(body.getContent()).isEmpty();
        assertThat(body.getContentType()).isEqualTo(MediaType.MULTIPART_FORM_DATA);
    }

    @Test
    void shouldStoreBodyUnderTheExecutionWhenFetchTypeIsStore() throws IOException {
        // Given
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        WebhookBodyService service = new WebhookBodyService(storage(captured));
        byte[] content = "an export".getBytes(StandardCharsets.UTF_8);

        // When
        WebhookBodyService.Body body = service.read(
            request(MediaType.APPLICATION_OCTET_STREAM, content),
            FLOW,
            EXECUTION_ID,
            FetchType.STORE
        );

        // Then - the body is stored rather than carried, so the trigger only gets its URI
        assertThat(body.storedUri())
            .isEqualTo(URI.create("kestra:///io/kestra/tests/webhook/executions/" + EXECUTION_ID + "/webhook/body"));
        assertThat(body.requestBody()).isNull();
        assertThat(captured.toByteArray()).isEqualTo(content);
    }

    @Test
    void shouldNotStoreAnythingWhenFetchTypeIsStoreAndRequestHasNoBody() throws IOException {
        // Given
        StorageInterface storage = storage();
        WebhookBodyService service = new WebhookBodyService(storage);

        // When
        WebhookBodyService.Body body = service.read(request(null, new byte[0]), FLOW, EXECUTION_ID, FetchType.STORE);

        // Then - an empty file under the execution would be a URI pointing at nothing
        assertThat(body.storedUri()).isNull();
        assertThat(body.requestBody()).isNull();
        verifyNoInteractions(storage);
    }

    @Test
    void shouldReadBodyOffTheConnectionAndDropItWhenFetchTypeIsNone() throws IOException {
        // Given — a streaming body, so that it being read off the connection is observable
        StorageInterface storage = mock(StorageInterface.class);
        WebhookBodyService service = new WebhookBodyService(storage);
        AtomicBoolean consumed = new AtomicBoolean();
        ByteBody byteBody = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
            .adapt(
                Flux.just(ReadBufferFactory.getJdkFactory().adapt("ignored".getBytes(StandardCharsets.UTF_8)))
                    .doOnComplete(() -> consumed.set(true))
            );

        // When
        WebhookBodyService.Body body = service.read(request(MediaType.APPLICATION_JSON, byteBody), FLOW, EXECUTION_ID, FetchType.NONE);

        // Then - nothing of the body reaches the flow, but it was read so the caller is not cut short
        assertThat(body.requestBody()).isNull();
        assertThat(body.storedUri()).isNull();
        assertThat(consumed).isTrue();
        verifyNoInteractions(storage);
    }

    @Test
    void shouldKeepBinaryBodyIntactWhenFetchTypeIsFetch() throws IOException {
        // Given
        StorageInterface storage = mock(StorageInterface.class);
        WebhookBodyService service = new WebhookBodyService(storage);
        byte[] content = { (byte) 0xC3, (byte) 0x28, (byte) 0xFF };

        // When
        WebhookBodyService.Body body = service.read(
            request(MediaType.APPLICATION_OCTET_STREAM, content),
            FLOW,
            EXECUTION_ID,
            FetchType.FETCH
        );

        // Then
        assertThat(body.storedUri()).isNull();
        assertThat(((ByteArrayRequestBody) body.requestBody()).getContent()).isEqualTo(content);
        verifyNoInteractions(storage);
    }

    @Test
    void shouldDeleteEverythingStoredForTheExecutionWhenCallCreatesNone() throws IOException {
        // Given
        StorageInterface storage = storage();
        WebhookBodyService service = new WebhookBodyService(storage);

        // When
        service.deleteStored(FLOW, EXECUTION_ID);

        // Then - the body and the parts alike, and nothing of another execution
        verify(storage).deleteByPrefix(
            FLOW.getTenantId(),
            FLOW.getNamespace(),
            URI.create("///io/kestra/tests/webhook/executions/" + EXECUTION_ID + "/webhook")
        );
    }

    /**
     * @return a storage that echoes back the URI it is asked to store at, prefixed with the internal storage scheme
     */
    private static StorageInterface storage() throws IOException {
        return storage(OutputStream.nullOutputStream());
    }

    /**
     * @param captured where the content the storage is asked to store is written, as it must be read before the
     *                 service closes the stream it hands over
     * @return a storage that echoes back the URI it is asked to store at, prefixed with the internal storage scheme
     */
    private static StorageInterface storage(OutputStream captured) throws IOException {
        StorageInterface storage = mock(StorageInterface.class);
        when(storage.put(eq(FLOW.getTenantId()), eq(FLOW.getNamespace()), any(URI.class), any(InputStream.class)))
            .thenAnswer(invocation ->
            {
                invocation.getArgument(3, InputStream.class).transferTo(captured);
                return URI.create("kestra://" + invocation.getArgument(2, URI.class).getPath());
            });
        return storage;
    }

    // Micronaut 5 made ByteBody a sealed interface, so it cannot be mocked; build a real heap-backed one.
    private static ServerHttpRequest<?> request(String contentType, byte[] content) {
        return request(contentType, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(content));
    }

    private static ServerHttpRequest<?> request(String contentType, ByteBody byteBody) {
        ServerHttpRequest<?> request = mock(ServerHttpRequest.class);
        when(request.byteBody()).thenReturn(byteBody);
        when(request.getContentType()).thenReturn(Optional.ofNullable(contentType).map(MediaType::of));

        return request;
    }

    private static MultipartBody body(CompletedPart... parts) {
        Flux<CompletedPart> flux = Flux.just(parts);
        return flux::subscribe;
    }

    // Micronaut 5 made CompletedPart/CompletedFileUpload sealed abstract classes whose metadata accessors are
    // final, so they can no longer be mocked; build real in-memory parts through the provided factories instead.
    private static CompletedFileUpload fileUpload(String name, String filename, String contentType, byte[] content) {
        return CompletedFileUpload.ofMemory(
            new FormFieldMetadata(name, filename, MediaType.of(contentType)),
            ReadBufferFactory.getJdkFactory().adapt(content)
        );
    }

    private static CompletedPart formField(String name, String content) {
        return CompletedAttribute.create(
            new FormFieldMetadata(name, null, null),
            ReadBufferFactory.getJdkFactory().adapt(content.getBytes(StandardCharsets.UTF_8))
        );
    }
}

package io.kestra.webserver.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.HttpRequest.MultipartFormDataRequestBody;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.storages.StorageInterface;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.server.multipart.MultipartBody;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookMultipartServiceTest {
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
        WebhookMultipartService service = new WebhookMultipartService(storage);
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
        WebhookMultipartService service = new WebhookMultipartService(storage);
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
        WebhookMultipartService service = new WebhookMultipartService(storage);
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
        WebhookMultipartService service = new WebhookMultipartService(mock(StorageInterface.class));

        // When
        MultipartFormDataRequestBody body = service
            .collect(null, FLOW, EXECUTION_ID, MediaType.MULTIPART_FORM_DATA)
            .block();

        // Then
        assertThat(body.getContent()).isEmpty();
        assertThat(body.getContentType()).isEqualTo(MediaType.MULTIPART_FORM_DATA);
    }

    /**
     * @return a storage that echoes back the URI it is asked to store at, prefixed with the internal storage scheme
     */
    private static StorageInterface storage() throws IOException {
        StorageInterface storage = mock(StorageInterface.class);
        when(storage.put(eq(FLOW.getTenantId()), eq(FLOW.getNamespace()), any(URI.class), any(InputStream.class)))
            .thenAnswer(invocation -> URI.create("kestra://" + invocation.getArgument(2, URI.class).getPath()));
        return storage;
    }

    private static MultipartBody body(CompletedPart... parts) {
        Flux<CompletedPart> flux = Flux.just(parts);
        return flux::subscribe;
    }

    private static CompletedFileUpload fileUpload(String name, String filename, String contentType, byte[] content) throws IOException {
        CompletedFileUpload part = mock(CompletedFileUpload.class);
        when(part.getName()).thenReturn(name);
        when(part.getFilename()).thenReturn(filename);
        when(part.getContentType()).thenReturn(Optional.of(MediaType.of(contentType)));
        when(part.getSize()).thenReturn((long) content.length);
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        return part;
    }

    private static CompletedPart formField(String name, String content) throws IOException {
        CompletedPart part = mock(CompletedPart.class);
        when(part.getName()).thenReturn(name);
        when(part.getContentType()).thenReturn(Optional.empty());
        when(part.getBytes()).thenReturn(content.getBytes(StandardCharsets.UTF_8));
        return part;
    }
}

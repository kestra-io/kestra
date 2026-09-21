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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.http.HttpRequest.ByteArrayRequestBody;
import io.kestra.core.http.HttpRequest.MultipartFormDataRequestBody;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger.FetchType;
import io.kestra.plugin.core.trigger.Webhook;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.server.multipart.MultipartBody;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
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

    private static final String SIGNED_JSON = "{ \"count\": 1.00, \"message\": \"café\", \"ok\": true }\n";
    private static final String SHA256 = "0d541f9ce82c02ab31853d0d99f7989d4dd1e178e1fa0643c4221648013cb22c";

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
        assertThat(parts.getFirst()).isEqualTo(
            new MultipartFormDataRequestBody.FilePart(
                "photo",
                "result.jpg",
                MediaType.IMAGE_JPEG,
                6L,
                URI.create("kestra:///io/kestra/tests/webhook/executions/" + EXECUTION_ID + "/webhook/0/result.jpg")
            )
        );
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

    @ParameterizedTest
    @CsvSource(
        {
            "HMAC_SHA1,1971a2d998072ecf97361209513a42389123678e",
            "HMAC_SHA256,0d541f9ce82c02ab31853d0d99f7989d4dd1e178e1fa0643c4221648013cb22c",
            "HMAC_SHA512,0907a0b2505eb686a8ea385398f96cde95b07d7abdc49d21eeb4188f13386e30d9bcc689dededfe83aa69bf5ade6d52e6b378474e327ebf488d7158e60a712b7"
        }
    )
    void shouldVerifyOriginalJsonBytesForEachAlgorithm(Webhook.SignatureAlgorithm algorithm, String digest) throws Exception {
        Webhook webhook = signedWebhook(FetchType.FETCH, algorithm, null);
        WebhookBodyService service = new WebhookBodyService(storage());
        var body = service.read(
            signedRequest(SIGNED_JSON.getBytes(StandardCharsets.UTF_8), digest.toUpperCase()),
            FLOW, EXECUTION_ID, webhook, runContext(webhook)
        );
        assertThat(body.requestBody().getContent()).isEqualTo(SIGNED_JSON);
    }

    @ParameterizedTest
    @EnumSource(FetchType.class)
    void shouldVerifySignatureForEveryFetchType(FetchType fetchType) throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        WebhookBodyService service = new WebhookBodyService(storage(captured));
        Webhook webhook = signedWebhook(fetchType, Webhook.SignatureAlgorithm.HMAC_SHA256, "sha256=");
        var body = service.read(
            signedRequest(SIGNED_JSON.getBytes(StandardCharsets.UTF_8), "sha256=" + SHA256),
            FLOW, EXECUTION_ID, webhook, runContext(webhook)
        );
        if (FetchType.STORE == fetchType) {
            assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo(SIGNED_JSON);
            assertThat(body.storedUri()).isNotNull();
            assertThat(body.requestBody()).isNull();
        } else if (FetchType.NONE == fetchType) {
            assertThat(body.requestBody()).isNull();
            assertThat(body.storedUri()).isNull();
        } else {
            assertThat(body.requestBody().getContent()).isEqualTo(SIGNED_JSON);
        }
    }

    @ParameterizedTest
    @EnumSource(FetchType.class)
    void shouldRejectTamperedBodyAndCleanUpStoredContent(FetchType fetchType) throws Exception {
        StorageInterface storage = storage();
        WebhookBodyService service = new WebhookBodyService(storage);
        Webhook webhook = signedWebhook(fetchType, Webhook.SignatureAlgorithm.HMAC_SHA256, null);
        assertThatThrownBy(
            () -> service.read(
                signedRequest("{}".getBytes(StandardCharsets.UTF_8), SHA256),
                FLOW, EXECUTION_ID, webhook, runContext(webhook)
            )
        )
            .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat((Object) e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        if (FetchType.STORE == fetchType) {
            verify(storage).deleteByPrefix(
                FLOW.getTenantId(), FLOW.getNamespace(),
                URI.create("///io/kestra/tests/webhook/executions/" + EXECUTION_ID + "/webhook")
            );
        } else {
            verifyNoInteractions(storage);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = { "sha256=bad", "sha1=1971a2d998072ecf97361209513a42389123678e",
            "sha256=zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
            "0d541f9ce82c02ab31853d0d99f7989d4dd1e178e1fa0643c4221648013cb22c" }
    )
    void shouldRejectMissingOrMalformedSignature(String value) throws Exception {
        StorageInterface storage = storage();
        WebhookBodyService service = new WebhookBodyService(storage);
        Webhook webhook = signedWebhook(FetchType.STORE, Webhook.SignatureAlgorithm.HMAC_SHA256, "sha256=");
        assertThatThrownBy(
            () -> service.read(
                signedRequest(SIGNED_JSON.getBytes(StandardCharsets.UTF_8), value),
                FLOW, EXECUTION_ID, webhook, runContext(webhook)
            )
        )
            .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat((Object) e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(storage);
    }

    @Test
    void shouldRejectDuplicateSignatureHeaders() throws Exception {
        var request = signedRequest(SIGNED_JSON.getBytes(StandardCharsets.UTF_8), SHA256);
        when(request.getHeaders().getAll("X-Signature")).thenReturn(List.of(SHA256, SHA256));
        Webhook webhook = signedWebhook(FetchType.FETCH, Webhook.SignatureAlgorithm.HMAC_SHA256, null);
        assertThatThrownBy(() -> new WebhookBodyService(storage()).read(request, FLOW, EXECUTION_ID, webhook, runContext(webhook)))
            .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat((Object) e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @ParameterizedTest
    @EnumSource(FetchType.class)
    void shouldVerifyEmptyBodyWithoutStoringAFile(FetchType fetchType) throws Exception {
        StorageInterface storage = storage();
        Webhook webhook = signedWebhook(fetchType, Webhook.SignatureAlgorithm.HMAC_SHA256, null);
        var body = new WebhookBodyService(storage).read(
            signedRequest(
                new byte[0],
                "a41bc6d81d6413576ae0994995e0ad89a416ec97389515c3604f47722122eeeb"
            ),
            FLOW, EXECUTION_ID, webhook, runContext(webhook)
        );
        assertThat(body.storedUri()).isNull();
        verifyNoInteractions(storage);
    }

    @Test
    void shouldRejectSignedMultipartBeforeStoringParts() {
        WebhookBodyService service = new WebhookBodyService(mock(StorageInterface.class));
        Webhook webhook = signedWebhook(FetchType.FETCH, Webhook.SignatureAlgorithm.HMAC_SHA256, null);
        assertThatThrownBy(() -> service.validateMultipartSignature(webhook))
            .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat((Object) e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        service.validateMultipartSignature(Webhook.builder().build());
    }

    @Test
    void shouldVerifyBinaryBytesWithoutDecodingThem() throws Exception {
        byte[] bytes = { (byte) 0xc3, 0x28, (byte) 0xff, 0 };
        var request = signedRequest(bytes, "01661ec70a8e44038181ccea03999f4b9bd94a5376cd24ec2700978bf1878391");
        when(request.getContentType()).thenReturn(Optional.of(MediaType.APPLICATION_OCTET_STREAM_TYPE));
        Webhook webhook = signedWebhook(FetchType.FETCH, Webhook.SignatureAlgorithm.HMAC_SHA256, null);
        var body = new WebhookBodyService(storage()).read(request, FLOW, EXECUTION_ID, webhook, runContext(webhook));
        assertThat(((ByteArrayRequestBody) body.requestBody()).getContent()).isEqualTo(bytes);
    }

    @Test
    void shouldStreamSignedLargeBodyIntoStorageBeforeReadingItAll() throws Exception {
        long[] remaining = { 16 * 1024 * 1024 };
        InputStream content = new InputStream() {
            @Override
            public int read() {
                return remaining[0]-- > 0 ? 'x' : -1;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                if (remaining[0] <= 0) {
                    return -1;
                }
                int count = (int) Math.min(remaining[0], length);
                java.util.Arrays.fill(bytes, offset, offset + count, (byte) 'x');
                remaining[0] -= count;
                return count;
            }
        };
        StorageInterface storage = mock(StorageInterface.class);
        when(storage.put(any(), any(), any(), any(InputStream.class))).thenAnswer(invocation ->
        {
            assertThat(remaining[0]).isGreaterThan(15 * 1024 * 1024);
            invocation.getArgument(3, InputStream.class).transferTo(OutputStream.nullOutputStream());
            return URI.create("kestra:///body");
        });
        var request = signedRequest(new byte[0], "917cd94ca83d2ca17601f5368b12dd904254191613a3505cd0b48d09504134ec");
        ByteBody byteBody = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(
            Flux.generate(sink ->
            {
                try {
                    byte[] chunk = content.readNBytes(8192);
                    if (chunk.length == 0) {
                        sink.complete();
                    } else {
                        sink.next(ReadBufferFactory.getJdkFactory().adapt(chunk));
                    }
                } catch (IOException e) {
                    sink.error(e);
                }
            })
        );
        when(request.byteBody()).thenReturn(byteBody);
        Webhook webhook = signedWebhook(FetchType.STORE, Webhook.SignatureAlgorithm.HMAC_SHA256, null);
        var body = new WebhookBodyService(storage).read(request, FLOW, EXECUTION_ID, webhook, runContext(webhook));
        assertThat(body.requestBody()).isNull();
        assertThat(body.storedUri()).isEqualTo(URI.create("kestra:///body"));
    }

    private static Webhook signedWebhook(FetchType fetchType, Webhook.SignatureAlgorithm algorithm, String prefix) {
        return Webhook.builder().fetchType(fetchType).signature(
            Webhook.Signature.builder()
                .header("X-Signature").algorithm(algorithm).prefix(prefix).secret(Property.ofValue("test-secret")).build()
        ).build();
    }

    private static RunContext runContext(Webhook webhook) throws Exception {
        RunContext context = mock(RunContext.class, RETURNS_DEEP_STUBS);
        when(context.render(webhook.getSignature().getSecret()).as(String.class)).thenReturn(Optional.of("test-secret"));
        return context;
    }

    private static ServerHttpRequest<?> signedRequest(byte[] content, String signature) {
        var request = request(MediaType.APPLICATION_JSON, content);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAll("X-Signature")).thenReturn(signature == null ? List.of() : List.of(signature));
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    /**
     * @return a storage that echoes back the URI it is asked to store at, prefixed with the internal storage scheme
     */
    private static StorageInterface storage() throws IOException {
        return storage(OutputStream.nullOutputStream());
    }

    /**
     * @param captured where the content the storage is asked to store is written, as it must be read before the
     *        service closes the stream it hands over
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

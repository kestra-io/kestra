package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import io.micronaut.http.multipart.CompletedPart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipartUtilsTest {
    @Test
    void shouldKeepTheResultWhenReleasing() {
        assertThat(MultipartUtils.releasing(Flux.<CompletedPart> empty(), Mono.just("ok")).block()).isEqualTo("ok");
    }

    @Test
    void shouldKeepEmptyWhenReleasing() {
        assertThat(MultipartUtils.releasing(null, Mono.<String> empty()).blockOptional()).isEmpty();
    }

    @Test
    void shouldKeepTheErrorWhenReleasing() {
        IllegalStateException failure = new IllegalStateException("rejected");

        assertThatThrownBy(() -> MultipartUtils.releasing(Flux.<CompletedPart> empty(), Mono.error(failure)).block()).isSameAs(failure);
    }

    @Test
    void shouldDiscardWhenThereIsNoBody() {
        MultipartUtils.release(null);

        assertThat(MultipartUtils.discard(null).blockOptional()).isEmpty();
    }

    @Test
    void shouldKeepTheErrorWhenTheActionThrowsWhileGuarding() {
        IllegalStateException failure = new IllegalStateException("rejected");

        assertThatThrownBy(() -> MultipartUtils.guard(Flux.<CompletedPart> empty(), () -> {
            throw failure;
        }).block()).isSameAs(failure);
    }

    @Test
    void shouldKeepTheResultWhenPartsCannotBeReadAgain() {
        assertThat(MultipartUtils.releasing(Flux.<CompletedPart> error(new IllegalStateException("already consumed")), Mono.just("ok")).block()).isEqualTo("ok");
    }
}

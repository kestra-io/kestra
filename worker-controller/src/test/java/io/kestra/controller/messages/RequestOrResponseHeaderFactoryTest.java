package io.kestra.controller.messages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.utils.EditionProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestOrResponseHeaderFactoryTest {

    @AfterEach
    void resetContext() {
        KestraContext.setContext(null);
    }

    @Test
    void shouldSetOssEditionWhenContextReportsOss() {
        assertThat(headerWithEdition(EditionProvider.Edition.OSS).getEdition()).isEqualTo(RequestOrResponseHeader.Edition.EDITION_OSS);
    }

    @Test
    void shouldSetEeEditionWhenContextReportsEe() {
        assertThat(headerWithEdition(EditionProvider.Edition.EE).getEdition()).isEqualTo(RequestOrResponseHeader.Edition.EDITION_EE);
    }

    @Test
    void shouldFallBackToUnknownEditionWhenContextReportsNone() {
        assertThat(headerWithEdition(null).getEdition()).isEqualTo(RequestOrResponseHeader.Edition.EDITION_UNSPECIFIED);
    }

    private static RequestOrResponseHeader headerWithEdition(EditionProvider.Edition edition) {
        KestraContext context = mock(KestraContext.class);
        when(context.getVersion()).thenReturn("test");
        when(context.getEdition()).thenReturn(edition);
        KestraContext.setContext(context);

        return RequestOrResponseHeaderFactory.create("client-1");
    }
}

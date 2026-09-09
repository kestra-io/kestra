package io.kestra.core.models.assets;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.FlowAction;
import io.kestra.core.models.validations.ModelValidator;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class AssetValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldAcceptCrnFormattedIdWithColons() {
        Custom asset = Custom.builder()
            .namespace("io.kestra")
            .id("crn:aws:s3:eu-west-1:123456789012:bucket")
            .type("MY_OWN_ASSET_TYPE")
            .build();

        assertThat(modelValidator.isValid(asset)).isEmpty();
    }

    @Test
    void shouldAcceptPlainId() {
        Custom asset = Custom.builder()
            .namespace("io.kestra")
            .id("my-asset_1.0")
            .type("MY_OWN_ASSET_TYPE")
            .build();

        assertThat(modelValidator.isValid(asset)).isEmpty();
    }

    @Test
    void shouldRejectIdStartingWithColon() {
        Custom asset = Custom.builder()
            .namespace("io.kestra")
            .id(":crn:aws:s3:bucket")
            .type("MY_OWN_ASSET_TYPE")
            .build();

        assertThat(modelValidator.isValid(asset))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectIdWithWhitespace() {
        Custom asset = Custom.builder()
            .namespace("io.kestra")
            .id("crn:aws s3:bucket")
            .type("MY_OWN_ASSET_TYPE")
            .build();

        assertThat(modelValidator.isValid(asset))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectAnAssetActionWithABlankNamespaceOrFlowId() {
        Asset asset = Custom.builder()
            .namespace("io.kestra")
            .id("my-asset")
            .type("MY_OWN_ASSET_TYPE")
            .build()
            .withAssetActions(List.of(new FlowAction("", null, null)));

        assertThat(modelValidator.isValid(asset))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
    }
}

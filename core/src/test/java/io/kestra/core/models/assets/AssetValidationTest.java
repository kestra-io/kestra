package io.kestra.core.models.assets;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.models.FieldCondition;
import io.kestra.core.models.dashboards.filters.AbstractFilter.FilterType;
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

    @ParameterizedTest
    @CsvSource(
        {
            "//compute.googleapis.com/projects/my-project/zones/europe-west1-b/instances/vm-1, compute.googleapis.com:projects:my-project:zones:europe-west1-b:instances:vm-1",
            "/subscriptions/0b9a2e4c/resourceGroups/rg-orders/providers/Microsoft.Compute/virtualMachines/vm-1, subscriptions:0b9a2e4c:resourceGroups:rg-orders:providers:Microsoft.Compute:virtualMachines:vm-1",
            "arn:aws:ec2:eu-west-1:123456789012:instance/i-0abc1234def567890, arn:aws:ec2:eu-west-1:123456789012:instance:i-0abc1234def567890",
            "https://example.com/orders/table, https:example.com:orders:table"
        }
    )
    void shouldAcceptACloudResourceNameOrAUrlAsAnIdWithItsSlashesTurnedIntoColons(String id, String converted) {
        Custom asset = Custom.builder()
            .namespace("io.kestra")
            .id(id)
            .type("MY_OWN_ASSET_TYPE")
            .build();

        assertThat(asset.getId()).isEqualTo(converted);
        assertThat(asset.getMetadata()).containsEntry(Asset.FULL_ID_METADATA_KEY, id);
        assertThat(modelValidator.isValid(asset)).isEmpty();
    }

    @Test
    void shouldAcceptTheAssetWhenItsIdIsLongerThanTheLimit() {
        Custom asset = Custom.builder()
            .namespace("io.kestra")
            .id("/subscriptions/0b9a2e4c-1f3d-4a5b-9c8d-7e6f5a4b3c2d/resourceGroups/" + "rg".repeat(45) + "/providers/Microsoft.Compute/virtualMachines/vm-1")
            .type("MY_OWN_ASSET_TYPE")
            .build();

        assertThat(modelValidator.isValid(asset)).isEmpty();
    }

    @Test
    void shouldRejectTheAssetWhenTheEndOfAnIdLongerThanTheLimitHoldsAForbiddenCharacter() {
        Custom asset = Custom.builder()
            .namespace("io.kestra")
            .id("/subscriptions/" + "rg".repeat(70) + "/virtual machines/vm-1")
            .type("MY_OWN_ASSET_TYPE")
            .build();

        assertThat(modelValidator.isValid(asset))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
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
    void shouldAcceptTheTtlFormatTheExpiryBucketsCompareAgainst() {
        assertThat(modelValidator.isValid(assetWithTtl("2026-09-15T12:00:00.000Z"))).isEmpty();
    }

    @Test
    void shouldAcceptAnEmptyTtl() {
        assertThat(modelValidator.isValid(assetWithTtl(""))).isEmpty();
    }

    @Test
    void shouldRejectATtlCarryingAnOffsetRatherThanUtc() {
        assertThat(modelValidator.isValid(assetWithTtl("2026-09-15T14:00:00.000+02:00")))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectATtlWithoutMillis() {
        assertThat(modelValidator.isValid(assetWithTtl("2026-09-15T12:00:00Z")))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
    }

    private Asset assetWithTtl(String ttl) {
        Asset asset = Custom.builder()
            .namespace("io.kestra")
            .id("my-asset")
            .type("MY_OWN_ASSET_TYPE")
            .build();
        asset.setTtl(ttl);
        return asset;
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

    @Test
    void shouldRejectAConditionThatComparesToNoValue() {
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("status", FilterType.EQUAL_TO, null))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("status", FilterType.IS_NULL, null))))
            .isEmpty();
    }

    @Test
    void shouldRejectAnInConditionWhoseValueIsNotAList() {
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("status", FilterType.IN, "active,expiring"))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("status", FilterType.IN, List.of("active", "expiring")))))
            .isEmpty();
    }

    @Test
    void shouldRejectAListValueForAnOperatorThatComparesASingleValue() {
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("status", FilterType.EQUAL_TO, List.of("active", "expiring")))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("status", FilterType.NOT_EQUAL_TO, List.of("destroyed")))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectAnOperatorThatDoesNotCompareAFieldToAValue() {
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("status", FilterType.OR, "active"))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("metadata.enabled", FilterType.IS_TRUE, "true"))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldValidateANonTextRegexValueOnItsTextForm() {
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("id", FilterType.REGEX, 42))))
            .isEmpty();
    }

    @Test
    void shouldRejectARegexConditionProneToCatastrophicBacktracking() {
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("id", FilterType.REGEX, "(a+)+$"))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectAConditionOnAFieldTheAssetDoesNotHave() {
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("region", FilterType.EQUAL_TO, "eu"))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("metadata.", FilterType.EQUAL_TO, "x"))))
            .get()
            .isInstanceOf(ConstraintViolationException.class);
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("metadata.region", FilterType.EQUAL_TO, "eu"))))
            .isEmpty();
        assertThat(modelValidator.isValid(assetWithCondition(new FieldCondition("owner", FilterType.IS_NULL, null))))
            .isEmpty();
    }

    private static Asset assetWithCondition(FieldCondition condition) {
        return Custom.builder()
            .namespace("io.kestra")
            .id("my-asset")
            .type("MY_OWN_ASSET_TYPE")
            .build()
            .withAssetActions(List.of(new FlowAction("io.kestra", "resize", null, List.of(condition))));
    }
}
